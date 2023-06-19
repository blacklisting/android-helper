package com.blacklisting.ds.github

import com.alibaba.fastjson2.annotation.JSONField

data class Permissions(
    @JSONField(name = "admin") val admin: Boolean,
    @JSONField(name = "maintain") val maintain: Boolean,
    @JSONField(name = "push") val push: Boolean,
    @JSONField(name = "triage") val triage: Boolean,
    @JSONField(name = "pull") val pull: Boolean
)
