package com.github.omarmiatello.gdgtools

import com.github.omarmiatello.gdgtools.appengine.cacheUriOr
import com.github.omarmiatello.gdgtools.data.*
import com.github.omarmiatello.gdgtools.utils.formatFull
import com.github.omarmiatello.gdgtools.utils.toJsonPretty
import com.github.omarmiatello.gdgtools.utils.weekRangeFrom
import io.ktor.application.call
import io.ktor.html.Template
import io.ktor.html.insert
import io.ktor.html.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.route
import kotlinx.html.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.list
import kotlinx.serialization.map
import kotlinx.serialization.serializer
import java.util.*


@Serializable
data class TagJsonResponse(val name: String, val slug: String, val hashtag: String)

fun Routing.gdg() {
    route("gdg") {
        get("groups.json") {
            call.respondText(cacheUriOr {
                val groups = FireDB.allGroups.toGroupResponseList()
                groups.toJsonPretty(GroupResponse.serializer().list)
            })
        }

        get("groups_events.json") {
            call.respondText(cacheUriOr {
                val eventsMap = FireDB.eventsMap
                val groupsAndEvents = FireDB.allGroups.map {
                    val events = eventsMap[it.slug]?.values?.toEventResponseList()
                    it.toResponse(events)
                }
                groupsAndEvents.toJsonPretty(GroupResponse.serializer().list)
            })
        }

        get("groups") {
            val eventsMap = FireDB.eventsMap
            val groupsAndEvents = FireDB.allGroups.map {
                val events = eventsMap[it.slug]?.values?.toEventResponseList()
                it.toResponse(events)
            }
            call.respondHtml {
                head { title { +"GDG Europe - Tools Project" } }
                body {
                    h1 { +"GDG Europe" }
                    p { a("groups.json") { +"groups.json" } }
                    p { a("groups_events.json") { +"groups_events.json" } }
                    p { +"There are ${groupsAndEvents.count()} GDG" }

                    h1 { +"Groups" }
                    groupsAndEvents.forEach { group ->
                        val events = group.events

                        div {
                            insert(group.toHtml(showGroupDescription = false)) {}
                            br
                            a("/gdg/groups/${group.slug}") { +"Ulteriori dettagli" }
                        }
                        h3 { +"Eventi (${events?.count() ?: 0})" }
                        p {
                            events?.forEach { event ->
                                insert(event.toHtml(showEventDescription = false)) {}
                                br
                            }
                        }
                    }
                }
            }
        }

        get("groups/{gdgId}.json") {
            cacheUriOr {
                val gdgId = call.parameters["gdgId"]!!
                val events = FireDB.getEventsBy(gdgId).values.toEventResponseList()
                val group = FireDB.getGroup(gdgId)?.toResponse(events)
                group?.toJsonPretty(GroupResponse.serializer()).orEmpty()
            }.also {
                if (it.isEmpty()) call.respond(HttpStatusCode.NotFound) else call.respondText(it)
            }
        }

        get("groups/{gdgId}") {
            val gdgId = call.parameters["gdgId"]!!
            val events = FireDB.getEventsBy(gdgId).values.toEventResponseList()
            val group = FireDB.getGroup(gdgId)!!.toResponse(events)
            call.respondHtml {
                head { title { +"GDG Europe - Tools Project" } }
                body {
                    a("$gdgId.json") { +"$gdgId.json" }
                    div {
                        insert(group.toHtml(showGroupDescription = true)) {}
                    }
                    h3 { +"Eventi (${events.count()})" }
                    p {
                        events.forEach { event ->
                            insert(event.toHtml(showEventDescription = true)) {}
                            br
                        }
                    }
                }
            }
        }

//        get("speakers.json") {
//            call.respondText(cacheUriOr {
//                val speakers = FireDB.speakersMap.values.toSpeakerResponseList()
//                speakers.toJsonPretty(SpeakerResponse.serializer().list)
//            })
//        }
//
//        get("speakers_slides.json") {
//            call.respondText(cacheUriOr {
//                val slidesMap = FireDB.slidesMap
//                val speakersAndSlides = FireDB.speakersMap.values.map {
//                    val slides = slidesMap[it.slug]?.values?.toSlideResponseList()
//                    it.toResponse(slides)
//                }
//                speakersAndSlides.toJsonPretty(SpeakerResponse.serializer().list)
//            })
//        }
//
//        get("speakers/{speaker}.json") {
//            cacheUriOr {
//                val speakerParam = call.parameters["speaker"]!!
//                val slides = FireDB.getSlidesBy(speakerParam).values.toSlideResponseList()
//                val speaker = FireDB.getSpeaker(speakerParam)?.toResponse(slides)
//                speaker?.toJsonPretty(SpeakerResponse.serializer()).orEmpty()
//            }.also {
//                if (it.isEmpty()) call.respond(HttpStatusCode.NotFound) else call.respondText(it)
//            }
//        }

        get("tag.json") {
            call.respondText(cacheUriOr {
                val tagMap = FireDB.tagsMap.values.toTagResponseList()
                tagMap.toJsonPretty(TagFullResponse.serializer().list)
            })
        }

        get("tag") {
            call.respondHtml {
                head { title { +"GDG Europe - Tools Project" } }
                body {
                    h1 { +"GDG Europe" }
                    knownTags.forEach {
                        p {
                            a("tag/${it.slug}") { +it.hashtag }
                            +" | "
                            a("tag/${it.slug}.json") { +"${it.slug}.json" }
                        }
                    }
                }
            }
        }

        get("tag/{tag}.json") {
            cacheUriOr {
                val tagParam = call.parameters["tag"]!!
                val tag = FireDB.getTag(tagParam)?.toFullResponse()
                tag?.toJsonPretty(TagFullResponse.serializer()).orEmpty()
            }.also {
                if (it.isEmpty()) call.respond(HttpStatusCode.NotFound) else call.respondText(it)
            }
        }

        get("tag/{tag}") {
            val tagParam = call.parameters["tag"]!!
            val tag = FireDB.getTag(tagParam)?.toFullResponse()
            if (tag != null) {
                val tagEvents = tag.event.orEmpty()
                call.respondHtml {
                    head { title { +"GDG Europe - Tools Project" } }
                    body {
                        h1 { +tag.hashtag }
                        a("$tagParam.json") { +"$tagParam.json" }
                        tagEvents.forEach { (groupSlug, eventSlugs) ->
                            h2 { +"$groupSlug (${eventSlugs.count()} eventi)" }
                            eventSlugs.forEach { eventSlug ->
                                p {
                                    a("/gdg/groups/$groupSlug") { +groupSlug }
                                    +eventSlug
                                }
                            }
                        }
                    }
                }
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("calendar/{year}.json") {
            cacheUriOr {
                val yearParam = call.parameters["year"]!!.toInt()
                val allEvents = FireDB.getAllEventByYear(yearParam).toEventResponseList()
                allEvents.toJsonPretty(EventResponse.serializer().list)
            }.also {
                if (it.isEmpty()) call.respond(HttpStatusCode.NotFound) else call.respondText(it)
            }
        }

        get("calendar/{year}") {
            val yearParam = call.parameters["year"]!!.toInt()
            val groupsMap = FireDB.groupsMap
            val allEvents = FireDB.getAllEventByYear(yearParam).toEventResponseList()

            call.respondHtml {
                head { title { +"GDG Europe - Tools Project" } }
                body {
                    h1 { +"GDG Europe" }
                    p { a("$yearParam.json") { +"$yearParam.json" } }
                    p { +"Ci sono ${allEvents.count()} eventi GDG nel $yearParam" }

                    h3 { +"Eventi $yearParam" }
                    p {
                        allEvents
                            .reversed()
                            .groupBy { it.dateString }
                            .forEach { (dateString, events) ->
                                h3 { +dateString }
                                events.forEach { event ->
                                    val group = groupsMap.getValue(event.groupSlug) //.toResponse(null)!!
                                    p {
                                        with(event) {
                                            b { +name }
                                            if (meetupLink != null) {
                                                +" ("
                                                a(meetupLink) { +"Link evento" }
                                                +")"
                                            }
                                            if (!tags.isNullOrEmpty()) {
                                                br
                                                tags.forEach { tag ->
                                                    a("/gdg/tag/${tag.slug}") { +tag.hashtag }
                                                    +" "
                                                }
                                            }
                                            br
                                            a(group.meetupLink) { +group.name }
                                            +" dalle $timeString - Durata: ${duration / 3600 / 1000f} ore"
                                            if (venueName != null) {
                                                br
                                                +"Location: "
                                                +"$venueName ($venueCity, $venueAddress - $venueCountry)"
                                            }
                                            br
                                        }
                                    }
                                }
                            }


                    }
                }
            }
        }

        get("next.json") {
            cacheUriOr {
                val eventsMap = FireDB.eventsMap

                val (start, end) = weekRangeFrom { add(Calendar.HOUR, 36) }
                val (nextWeekStart, nextWeekEnd) = weekRangeFrom {
                    add(Calendar.HOUR, 36)
                    add(Calendar.DAY_OF_MONTH, 7)
                }

                val events = eventsMap.values
                    .flatMap { it.values }
                    .filter { Date(it.time).after(start.time) }
                    .sortedBy { it.time }
                    .toEventResponseList()

                val (nextEvents, futureEvents1) = events.partition { Date(it.time).before(end.time) }
                val (nextWeekEvents, futureEvents2) = futureEvents1.partition { Date(it.time).before(nextWeekEnd.time) }

                val eventListSerializer = EventResponse.serializer().list
                mapOf(
                    "thisweek" to nextEvents,
                    "nextweek" to nextWeekEvents
                ).toJsonPretty( (String.serializer() to eventListSerializer).map)
            }.also {
                if (it.isEmpty()) call.respond(HttpStatusCode.NotFound) else call.respondText(it)
            }
        }

        get("next") {

            val groupsMap = FireDB.groupsMap
            val eventsMap = FireDB.eventsMap

            val (start, end) = weekRangeFrom { add(Calendar.HOUR, 36) }
            val (nextWeekStart, nextWeekEnd) = weekRangeFrom {
                add(Calendar.HOUR, 36)
                add(Calendar.DAY_OF_MONTH, 7)
            }

            val events = eventsMap.values
                .flatMap { it.values }
                .filter { Date(it.time).after(start.time) }
                .sortedBy { it.time }
                .toEventResponseList()

            val (nextEvents, futureEvents1) = events.partition { Date(it.time).before(end.time) }
            val (nextWeekEvents, futureEvents2) = futureEvents1.partition { Date(it.time).before(nextWeekEnd.time) }


            call.respondHtml {
                head { title { +"GDG Europe - Tools Project" } }


                body {
                    h1 { +"GDG Next Events" }
                    p { +"There are ${nextEvents.count()} events this week, ${nextWeekEvents.count()} events next week, and soon other ${futureEvents2.count()} GDG events!" }

                    fun showEvents(allEvents: List<EventResponse>) {
                        p {
                            allEvents
                                .groupBy { Date(it.time).formatFull() }
                                .forEach { (dateString, events) ->
                                    h3 { +dateString }
                                    events.forEach { event ->
                                        val group = groupsMap.getValue(event.groupSlug) //.toResponse(null)!!
                                        p {
                                            with(event) {
                                                b { +name }
                                                if (meetupLink != null) {
                                                    +" ("
                                                    a(meetupLink) { +"Link" }
                                                    +")"
                                                }
                                                br
                                                a(group.meetupLink) { +group.name }
                                                +" from $timeString - duration: ${duration / 3600 / 1000f} hours"
                                                if (venueName != null) {
                                                    br
                                                    +"Location: "
                                                    +"$venueName"
                                                    val address = listOfNotNull(venueAddress, venueCity).joinToString(", ")
                                                    if (address.isNotEmpty()) +" ($address)"
                                                }
                                                br
                                            }
                                        }
                                    }
                                }
                        }
                    }

                    h3 { +"This week" }
                    showEvents(nextEvents)

                    h3 { +"Next week" }
                    showEvents(nextWeekEvents)

//                    h3 { +"After" }
//                    showEvents(futureEvents2)
                }
            }
        }
    }
}

private fun GroupResponse.toHtml(showGroupDescription: Boolean) = object : Template<FlowContent> {
    override fun FlowContent.apply() {
        h2 {
            +("$name (")
            a(meetupLink) { +"Link gruppo" }
            +(")")
        }
        img(src = logoImageUrl)
        p {
            +"Persone registrate: $membersCount $membersName"
            br
            +"Luogo: $city"
            if (showGroupDescription && description != null) {
                br
                +"Descrizione: "
                unsafe { raw(description) }
            }
        }
    }
}

private fun EventResponse.toHtml(showEventDescription: Boolean) = object : Template<FlowContent> {
    override fun FlowContent.apply() {
        div {
            b { +name }
            if (meetupLink != null) {
                +" ("
                a(meetupLink) { +"Link evento" }
                +")"
            }
            if (!tags.isNullOrEmpty()) {
                br
                tags.forEach { tag ->
                    a("/gdg/tag/${tag.slug}") { +tag.hashtag }
                    +" "
                }
            }
            if (time != 0L) {
                br
                +"$dateString dalle $timeString - Durata: ${duration / 3600 / 1000f} ore"
            }
            if (venueName != null) {
                br
                +"Location: "
                +venueName
                val address = listOfNotNull(venueAddress, venueCity).joinToString(", ")
                if (address.isNotEmpty()) +" ($address)"
            }
            br
            +"Iscritti: $attendeeRsvpYesCount"
            if (attendeeRsvpLimit != 0) +"/$attendeeRsvpLimit"
            if (attendeeWaitlistCount != 0) +" (waitlist: $attendeeWaitlistCount)"
            if (attendeeManualCount != 0) {
                +" | Hanno partecipato: "
                b { +"$attendeeManualCount" }
            }
            if (showEventDescription && description != null) {
                br
                +"Descrizione: "
                unsafe { raw(description) }
            }
        }
    }
}