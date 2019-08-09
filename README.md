Jenkins IRC plugin [![Build Status](https://ci.jenkins.io/job/Plugins/job/ircbot-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/ircbot-plugin/job/master/)
==================

This plugin enables Jenkins to send build notífications via IRC and
lets you interact with Jenkins via an IRC bot. Like some other IM protocol
plugins, for most of the common IM capabilities it relies on the
[`instant-messaging-plugin`](https://github.com/jenkinsci/instant-messaging-plugin).

Among many other abilities, it can be used for hands-off notifications
about build status changes with a globally configured IRC channel
subscription, in both legacy freestyle jobs and newly with pipelines
as an `ircNotify` step.

Interesting usage examples for the pipeline step include:

* `ircNotify()` to alert default subscribed channels per global config,
using common notification strategies to filter messages about newly
introduced failures and fixes (the empty parenthesis mean to use the
default settings, very simply to code)

* `ircNotify targets: "username #channelname"` to alert certain recipients
(in particular, certain users via private chats) - note that the list gets
built into the job configuration rather than default subscribed channels

* `ircNotify customMessage: "Some text", targets: "adminname"` to always
post the specified string

* Note: due to constraints of the current implementation, please use a
special form of notification for reporting a start of pipeline build:
`ircNotify notifyOnStart:true` otherwise the step is treated as a build
completion notification with a `NOT BUILT` status (before some verdict
becomes available).

For more information, visit the wiki page:
<http://wiki.jenkins-ci.org/display/JENKINS/IRC+Plugin>
