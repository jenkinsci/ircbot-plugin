= IRCBot Plugin https://ci.jenkins.io/job/Plugins/job/ircbot-plugin/job/master/[image:https://ci.jenkins.io/job/Plugins/job/ircbot-plugin/job/master/badge/icon["Build Status"]]

This plugin enables Jenkins to send build notífications via IRC and
lets you interact with Jenkins via an IRC bot. Like some other IM protocol
plugins, for most of the common IM capabilities it relies on the
https://github.com/jenkinsci/instant-messaging-plugin[`instant-messaging-plugin`]

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

== Installation Requirements

[[IRCPlugin-Features]]
== Features

See the required
https://plugins.jenkins.io/instant-messaging/[instant-messaging-plugin]
for a description of protocol-independent features.

[[IRCPlugin-IRC-specificfeatures]]
=== IRC-specific features

* support password-protected chatrooms
* support NickServ authentication
* the rate at which messages are send is throttled to one every 500ms to
avoid being subject of flood control on the IRC server. +
This rate can be configured with the system
property hudson.plugins.ircbot.messageRate

[[IRCPlugin-Pipelinesyntaxfeatures]]
=== Pipeline syntax features

Starting with release 2.31 this plugin can be called as a pipeline step.
The same toggles as configurable in a legacy job post-build task can be
specified as named arguments to the pipeline step, with a difference
that they are executed at once.

[[IRCPlugin-Examplepipelinesteps]]
==== Example pipeline steps

Example `ircNotify` pipeline step syntax variants (not exhaustive): 

[source,syntaxhighlighter-pre]
----
pipeline {
    agent any
    options {
        disableConcurrentBuilds()
        skipDefaultCheckout()
    }
    stages {
        stage ('Notify') {
            steps {
// Notify a start of build; appends the extra message at the end (note: prefix with separators if needed)
                ircNotify notifyOnStart:true, extraMessage: "Running IRCBot test..."
            }
        }

        stage ('PM') {
            steps {
// Post a verbatim custom message; can specify non-default targets (defaults are in global config)
                ircNotify targets: "jim #myroom", customMessage: "Hello from IRCBot"
            }
        }
    }
    post {
        always {
// Notify the verdict only known at end of build; send to default targets by default notification strategy
//          ircNotify()
// ...or with a particular strategy:
            ircNotify notificationStrategy:"FAILURE_AND_FIXED"
        }
    }
}
----

with an output in the IRC room like the following:

[source,syntaxhighlighter-pre]
----
(06:37:03) jenkins2: (notice) Starting build #214 for job GitOrg » reponame » branchname Running IRCBot test... (previous build: SUCCESS)
(06:37:04) jenkins2: (notice) Hello from IRCBot
(06:37:05) jenkins2: (notice) Project GitOrg » reponame » branchname build #214: SUCCESS in 7.2 sec: https://jenkins.localdomain/job/GitOrg/job/reponame/job/branchname/214/
----

[[IRCPlugin-Pipelinesteparguments]]
==== Pipeline step arguments

The `ircNotify` step optionally accepts any of the following parameters,
and effectively passes them to the
https://plugins.jenkins.io/instant-messaging/[instant-messaging-plugin]
for practical application.

[width="100%",cols="12%,13%,75%",options="header",]
|===
|argument name |syntax |description
|`targets` |space-separated string |Send the notification (or a
"customMessage") to specified user name(s) and channel name(s), the
latter start with a hash sign.
|`notifyOnStart` |boolean |Set to *true* explicitly in an `ircNotify`
step reporting a start of build, instead of a build completion
|`notifySuspects` |boolean |Select if the (build completion) notification
should alarm the committers to (newly) failed builds
|`notifyCulprits` |boolean |Specifies if culprits - i.e. committers to
previous already failing builds - should be informed about subsequent
build failures.
|`notifyFixers` |boolean |Specifies if 'fixers' should be informed
about builds they fixed.
|`notifyUpstreamCommitters` |boolean |Specifies if upstream
committers should be informed about build failures.
|`extraMessage` |string |Append an additional message to usual
notifications about build start/completion (note: you may want
to start this string with a separator such as a semicolon)
|`customMessage` |string |*Instead* of build status messages, send
an arbitrary message to specified or default (global config) targets
with the pipeline step (and ignoring the strategy and filtering rules
options above)
|notificationStrategy |string a|
https://github.com/jenkinsci/instant-messaging-plugin/blob/master/src/main/java/hudson/plugins/im/NotificationStrategy.java

* `"ALL"` - No matter what, notifications should always be sent.
* `"ANY_FAILURE"` - Whenever there is a failure, a notification should be sent.
* `"FAILURE_AND_FIXED"` - Whenever there is a failure or a failure was fixed,
   a notification should be sent.
* `"NEW_FAILURE_AND_FIXED"` - Whenever there is a new failure or a failure was
   fixed, a notification should be sent. Similar to `FAILURE_AND_FIXED`, but
   repeated failures do not trigger a notification.
* `"STATECHANGE_ONLY"` - Notifications should be sent only if there was a
   change in the build state, or this was the first build.
|===

The following options can be specified, but not sure to what effect and
how (TODO: try in practice and document here):

[cols=",,",options="header",]
|===
|argument name |syntax |description
|buildToChatNotifier |class name?
|https://github.com/jenkinsci/instant-messaging-plugin/blob/master/src/main/java/hudson/plugins/im/IMPublisher.java#L88

|matrixMultiplier |string or java/groovy token? a|
* https://github.com/jenkinsci/instant-messaging-plugin/blob/master/src/main/java/hudson/plugins/im/IMPublisher.java#L89
* https://github.com/jenkinsci/instant-messaging-plugin/blob/master/src/main/java/hudson/plugins/im/MatrixJobMultiplier.java

e.g. MatrixJobMultiplier
* ONLY_CONFIGURATIONS
* ONLY_PARENT ALL
|===

[[IRCPlugin-KnownIssues]]
== Known Issues

Please look into the
http://issues.jenkins-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+JENKINS+AND+status+in+%28Open%2C+%22In+Progress%22%2C+Reopened%29+AND+component+%3D+%27ircbot-plugin%27[issue
tracker] for any open issues for this plugin.

[[IRCPlugin-DebuggingProblems]]
=== Debugging Problems

If you experience any problems using the plugin please increase the log
level of the logger `hudson.plugins.ircbot` to FINEST (see
https://www.jenkins.io/doc/book/system-administration/viewing-logs/[Logging]), try to
reproduce the problem and attach the collected logs to the JIRA issue.

[[IRCPlugin-Changelog]]
