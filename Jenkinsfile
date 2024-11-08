/*
 * See the documentation for more options:
 * https://github.com/jenkins-infra/pipeline-library/
 */
buildPlugin(
 useContainerAgent: true,
 configurations: [
  // Test the common case (i.e., a recent LTS release) on both Linux and Windows
  // with same core version as the lowest baseline requested by pom.xml
  [ platform: 'linux', jdk: '11', jenkins: '2.387.3' ],
  [ platform: 'windows', jdk: '11', jenkins: '2.387.3' ],

  // Test the bleeding edge of the compatibility spectrum (i.e., the latest supported Java runtime).
  // see also https://www.jenkins.io/doc/developer/plugin-development/choosing-jenkins-baseline/
  // NOTE: 2.475+ introduced other breaking changes to ecosystem
  //[ platform: 'linux', jdk: '17', jenkins: '2.479.1' ],

  // NOTE: LTS https://www.jenkins.io/changelog-stable/#v2.462.3
  // is the last LTS release to support Java 11
  [ platform: 'linux', jdk: '11', jenkins: '2.462.3' ],
  [ platform: 'linux', jdk: '17', jenkins: '2.462.3' ],
  [ platform: 'linux', jdk: '21', jenkins: '2.462.3' ],
])
