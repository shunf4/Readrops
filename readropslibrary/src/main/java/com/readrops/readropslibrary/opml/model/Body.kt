package com.readrops.readropslibrary.opml.model

import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Root(name = "body", strict = false)
data class Body(@field:ElementList(inline = true, required = true) var outlines: List<Outline>?) {

    constructor() : this(null)
}