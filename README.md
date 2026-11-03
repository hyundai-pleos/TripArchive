# TripArchive

## CI

GitHub Actions의 `Android CI`는 `main` 대상 PR, `main` 푸시 시 실행됩니다.
Actions 탭의 **Run workflow**로 수동 실행할 수도 있습니다.

- 실행 환경: Ubuntu, Temurin JDK 21, Android SDK 37.0
- 검사: ktlint 코드 스타일 검사와 빌드·단위 테스트·Android Lint 작업을 병렬 실행
- 결과물: `app-debug`(디버그 APK)를 7일 보관하며 검사 보고서는 업로드하지 않음
- 동일 브랜치에 새 실행이 시작되면 이전 실행을 취소

로컬에서 같은 검사를 실행하려면 JDK 21과 Android SDK를 준비하고 다음 명령을 실행합니다.

```sh
./gradlew --no-daemon --stacktrace ktlintCheck
./gradlew --no-daemon --stacktrace --continue :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

코드 스타일은 `./gradlew ktlintFormat`으로 자동 정리할 수 있습니다.

CI는 에뮬레이터 없이 실행되며, `androidTest` 계측 테스트는 포함하지 않습니다.
현재 의존성 설정에는 별도 GitHub Secrets가 필요하지 않습니다.

PR 병합 전 CI 통과를 필수로 하려면 GitHub 브랜치 보호 규칙 또는 Ruleset에서
`Ktlint`, `Build, unit tests, and lint` 검사를 필수 상태 검사로 지정합니다.

참고: [Gradle Actions 설정](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md),
[Android SDK 설정](https://github.com/android-actions/setup-android).
