- Report the Gradle Build Tool running the build as an `org.gradle:gradle-core` dependency, so that GitHub can
  surface known vulnerabilities in the version of Gradle used to run the build. These are the coordinates that
  GitHub advisories for the Gradle Build Tool are published against. The entry is always reported as a 'direct'
  dependency with 'development' scope, and is not affected by the project, configuration or scope filters.
- Dependency verification: releases are now signed with a new Gradle Inc. PGP key. The previously documented key
  `7B79ADD11F8A779FE90FD3D0893A028475557671` was revoked, and must be replaced with the new signing subkey
  `E2879931BCA1A42E55F2D64DD9B2DFBD9F3298BA`. See 'Dependency verification' in the README.
