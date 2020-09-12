package com.readrops.api.localfeed.rss

import com.gitlab.mvysny.konsumexml.Names
import com.gitlab.mvysny.konsumexml.allChildrenAutoIgnore
import com.gitlab.mvysny.konsumexml.konsumeXml
import com.readrops.api.localfeed.XmlAdapter
import com.readrops.api.utils.ParseException
import com.readrops.api.utils.nonNullText
import com.readrops.api.utils.nullableText
import com.readrops.db.entities.Feed
import org.jsoup.Jsoup
import java.io.InputStream

class RSSFeedAdapter : XmlAdapter<Feed> {

    override fun fromXml(inputStream: InputStream): Feed {
        val konsume = inputStream.konsumeXml()
        val feed = Feed()

        return try {
            konsume.child("rss") {
                child("channel") {
                    allChildrenAutoIgnore(names) {
                        with(feed) {
                            when (tagName) {
                                "title" -> name = Jsoup.parse(nonNullText()).text()
                                "description" -> description = nullableText(failOnElement = false)
                                "link" -> siteUrl = nullableText()
                                "atom:link" -> url = attributes.getValueOpt("href")
                            }
                        }
                    }
                }
            }

            konsume.close()
            feed
        } catch (e: Exception) {
            throw ParseException(e.message)
        }
    }

    companion object {
        val names = Names.of("title", "description", "link")
    }
}