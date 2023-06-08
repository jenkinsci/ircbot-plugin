/*
 * See the documentation for more options:
 * https://github.com/jenkins-infra/pipeline-library/
 */
buildPlugin(
 useContainerAgent: true,
 // Opt-in to the Artifact Caching Proxy? (maybe to be removed when it will be opt-out)
 // See https://github.com/jenkins-infra/helpdesk/issues/2752 for more details and updates.
 // See also https://github.com/jenkins-infra/pipeline-library/pull/577
 //          https://github.com/jenkins-infra/pipeline-library/pull/522
 //          https://github.com/jenkins-infra/pipeline-library/pull/635
 useArtifactCachingProxy: true,
 configurations: [
  // Test the common case (i.e., a recent LTS release) on both Linux and Windows.
  [ platform: 'linux', jdk: '11', jenkins: '2.375.1' ],
  [ platform: 'windows', jdk: '11', jenkins: '2.375.1' ],

  // Test the bleeding edge of the compatibility spectrum (i.e., the latest supported Java runtime).
  // see also https://www.jenkins.io/doc/developer/plugin-development/choosing-jenkins-baseline/
  [ platform: 'linux', jdk: '17', jenkins: '2.375.2' ],
])
