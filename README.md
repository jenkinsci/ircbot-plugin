Jenkins IRC plugin [![Build Status](https://buildhive.cloudbees.com/job/jenkinsci/job/ircbot-plugin/badge/icon)](https://buildhive.cloudbees.com/job/jenkinsci/job/ircbot-plugin/)
==================

This plugin enables Jenkins to send build notífications via IRC and lets you interact with Jenkins via an IRC bot.

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

For more information, visit the wiki page:
<http://wiki.jenkins-ci.org/display/JENKINS/IRC+Plugin>

