package com.blacklisting.ds.github

import com.alibaba.fastjson2.annotation.JSONField

data class Owner(
    @JSONField(name = "login") val login: String,
    @JSONField(name = "id") val id: Int,
    @JSONField(name = "node_id") val nodeId: String,
    @JSONField(name = "avatar_url") val avatarUrl: String,
    @JSONField(name = "gravatar_id") val gravatarId: String,
    @JSONField(name = "url") val url: String,
    @JSONField(name = "html_url") val htmlUrl: String,
    @JSONField(name = "followers_url") val followersUrl: String,
    @JSONField(name = "following_url") val followingUrl: String,
    @JSONField(name = "gists_url") val gistsUrl: String,
    @JSONField(name = "starred_url") val starredUrl: String,
    @JSONField(name = "subscriptions_url") val subscriptionsUrl: String,
    @JSONField(name = "organizations_url") val organizationsUrl: String,
    @JSONField(name = "repos_url") val reposUrl: String,
    @JSONField(name = "events_url") val eventsUrl: String,
    @JSONField(name = "received_events_url") val receivedEventsUrl: String,
    @JSONField(name = "type") val type: String,
    @JSONField(name = "site_admin") val siteAdmin: Boolean
)
