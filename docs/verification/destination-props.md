# 목적지 분류별 3D 소품 검증

홈 화면은 순정 내비게이션 목적지에 제공된 분류값을 받아 해당 3D 소품 하나를 캐릭터 오른쪽에 표시한다. 현재 소품 파일은 `app/src/main/assets/models/destination_props/` 아래의 `mart.glb`, `school.glb`, `tourism.glb`, `restaurant.glb`, `hospital.glb`이다. 소품은 캐릭터와 같은 SceneView에 별도 모델 노드로 올리고, 캐릭터 모델 크기·modifier·카메라를 바꾸지 않는다.

카드와 도착 다이얼로그에는 동일한 분류 아이콘과 한국어 라벨을 표시한다. 자동 분류가 끝난 뒤 미분류인 목적지는 사용자가 직접 분류할 수 있다. 기타를 선택하거나 5초 동안 선택하지 않으면 홈과 도착 화면에서 분류 라벨 없이 기존 목적지 깃발을 사용한다.

## 분류 입력 경계

화면은 목적지 이름을 보고 분류하지 않는다. `DestinationState.current`와 `pendingArrival`에 포함된 카테고리를 사용한다. `PleosDestinationSource`는 SDK에서 받은 이름·좌표로 분류를 조회하고 같은 방문 ID의 목적지에만 결과를 반영한다. 도착 확인 대기의 분류가 없을 때 새 현재 목적지의 분류를 대신 사용하지 않는다. 음식점 분류 `FOOD`는 `restaurant.glb` 소품으로 표시한다. 목적지 이름 규칙이나 임의의 문자열 목록을 UI에 추가하지 않는다.

수동 선택은 방문 ID에 연결되며 동일 방문의 홈·도착 화면과 여행 기록에 반영된다. 자동 분류를 기다리는 동안에는 선택창을 표시하지 않는다. 도착이 먼저 발생하면 기존 도착 확인 흐름을 따른다. 기타는 화면과 여행 기록 모두에서 미분류로 유지된다. 수동 선택 없이 도착한 미분류 목적지에는 기존 기록 정책을 적용한다.

## 데모 앱 실행

```sh
./gradlew --offline :app:assembleDemo :app:installDemo
```

APK: `app/build/outputs/apk/demo/app-demo.apk`  
패키지: `com.triparchive.demo`. 데모 앱에서 프로필을 선택해 홈을 연다. 홈 왼쪽 위의 노란 `DEMO · 목적지 입력` 패널에서 다섯 분류와 `분류 없음`, `경로 취소`, `도착 재현`, `SDK 상태로 복귀`를 사용할 수 있다. 목적지를 바꾸면 분류 아이콘·라벨과 3D 소품도 함께 바뀐다. 분류 없음에서는 선택 다이얼로그가 표시된다. 기타를 선택하거나 시간이 만료되면 깃발 표시와 소품 숨김을 확인한다. 도착 재현은 현재 목적지의 분류를 도착 다이얼로그에 유지한다.

## ADB 입력

데모 화면을 연 상태에서 다음 브로드캐스트를 보낸다. 수신 action은 `com.triparchive.demo.DESTINATION_INPUT`이다. 이 receiver는 `src/demo`에만 포함된다.

```sh
# 마트 목적지 설정
adb shell am broadcast -a com.triparchive.demo.DESTINATION_INPUT -p com.triparchive.demo --es name "이마트 강릉점" --es category mart --ez routing true

# 관광 목적지로 교체
adb shell am broadcast -a com.triparchive.demo.DESTINATION_INPUT -p com.triparchive.demo --es name "경포대" --es category tourism --ez routing true

# 분류 없음 확인
adb shell am broadcast -a com.triparchive.demo.DESTINATION_INPUT -p com.triparchive.demo --es name "성심당 DCC점" --es category unknown --ez routing true

# 현재 경로 도착 처리. name/category/routing 없이 보내면 현재 데모 목적지를 사용한다.
adb shell am broadcast -a com.triparchive.demo.DESTINATION_INPUT -p com.triparchive.demo --ez arrived true

# 현재 경로 취소. 활성 목적지 이름과 분류를 지운다.
adb shell am broadcast -a com.triparchive.demo.DESTINATION_INPUT -p com.triparchive.demo --ez routing false

# 데모 입력을 해제하고 Pleos 목적지 상태로 복귀
adb shell am broadcast -a com.triparchive.demo.DESTINATION_INPUT -p com.triparchive.demo --ez reset true
```

`category`에 허용된 소문자 값은 `mart`, `school`, `tourism`, `restaurant`, `hospital`이다. `unknown` 또는 다른 값은 미분류로 처리한다. `arrived=true`는 활성 경로가 있어야 한다. `reset=true`는 다른 extra보다 우선한다. `routing=false`는 현재 데모 경로를 취소한다.

## 검증 명령

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --offline :app:ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:assembleDemo
```

`DestinationStateTest`는 분류 보존, 취소, 목적지 교체, 미분류 목적지, 이름이 일치하지 않는 도착을 확인한다. `DestinationHomeTest`는 도착 분류가 없을 때 새 활성 목적지의 분류를 도착 화면에 섞지 않는지 확인한다. ADB 입력과 Pleos에서 실제 분류를 공급하는 통합 검증은 데모 receiver 입력과 별도로 확인한다.

## 실행 결과 (2026-11-14)

Pleos Connect 2.1.0 / API 34, 사용자 10의 2분할 화면에서 아이 프로필로 확인했다. 데모 입력으로 마트 → 학교 → 관광 → 음식점 → 병원으로 바꿨을 때 각각 바구니, 책·연필, 카메라, 접시·클로슈, 의료 가방으로 교체됐다. 미분류와 경로 취소에서는 소품이 사라졌고, 병원 도착 다이얼로그에는 병원 분류가 유지됐다. 캐릭터 카메라·모델 스케일은 변경하지 않았고, 녹화에서 목적지 전환 중 같은 구도와 크기를 유지했다.

로컬 영상: `.state/props26/destination-props-pleos.mp4` (Git 제외). 데모 패널이 보이는 입력 재현 영상이며 실제 내비게이션 분류 연동 영상은 아니다. 실제 분류 제공은 #23 연결 후 별도 확인해야 한다. 성인·부모님 분할 화면 검증은 아래 후속 결과에 기록했다.

- `ktlintCheck`, `testDebugUnitTest`, `assembleDebug`, `assembleDemo` 통과.
- `assembleDebugAndroidTest`, `compileReleaseKotlin` 통과.
- Pleos에서 `arrivalCategoryDoesNotFallBackToTheNextActiveDestination` 계측 테스트 1개 통과 (1.067초).
- 독립 코드 리뷰에서 동일 목적지 도착 재현 시 확인 상태가 남는 문제를 수정하고 재리뷰했다.
- 5개 GLB 총 426,172바이트. 텍스처·스킨·애니메이션 없이 개별 10,000삼각형 미만이다.

## 커밋 구분

1. 목적지 분류·도착 상태 계약과 단위 테스트.
2. 경량 GLB 5종과 재생성 스크립트.
3. SceneView 소품 슬롯, 홈·도착 표시, 데모 입력 및 계측 테스트.
4. 검증 절차와 실제 실행 결과 기록.


## 분할 화면 렌더링 수정과 후속 검증 (2026-11-14)

수정 전에는 2→3분할 후 목적지 라벨만 바뀌고 3D 소품이 이전 상태로 멈췄다. 프로필 화면에서 먼저 3분할로 바꾼 뒤 홈에 진입하면 캐릭터가 표시되지 않는 경우도 있었다. 이때 로그에서 `EGL_BAD_MATCH`와 Filament의 `makeCurrent` 실패가 반복됐다. OpenGL 엔진 생성 방식만 바꾸는 실험은 증상을 해결하지 못해 되돌렸다.

분할 크기가 바뀌면 SceneView의 출력 표면과 렌더러를 다시 만들되, Filament Scene·모델 인스턴스·ModelNode·카메라·View는 유지한다. 외부 SceneNodeManager가 노드를 관리하고 새 렌더 루프에서 루트 노드의 `onFrame`을 호출한다. SceneView 전체에 크기 키만 넣는 중간안은 모델 로딩 중 해제를 유발해 `Texture is not created yet` 크래시가 발생했으므로 채택하지 않았다. 최종안은 모델 로딩 및 노드 수명을 크기 키 바깥으로 분리한다.

카메라 z=2.05, 캐릭터 scaleToUnits=1, 기존 홈 캐릭터 영역, 소품 scaleToUnits=0.22 및 위치 (0.5, -0.35, 0)는 유지했다. 데모 패널은 오른쪽 중앙에서 왼쪽 위(기존 화면 배율 기준 start 16dp/top 144dp)로 이동했다.

최종 데모 APK를 Pleos Connect 2.1.0 / API 34에 설치해 다음을 확인했다.

| 프로필 | 2분할 (1682×1210) | 3분할 (841×1210) |
| --- | --- | --- |
| 성인 | 다섯 소품 전환 및 미분류 숨김 정상 | 프로필에서 첫 진입, 다섯 소품 전환 및 미분류 숨김 정상 |
| 부모님 | 다섯 소품 전환 및 미분류 숨김 정상 | 프로필에서 첫 진입, 다섯 소품 전환 및 미분류 숨김 정상 |

성인·부모님 각각 2↔3분할을 세 차례 반복하고 학교·병원 소품을 번갈아 표시했으며, 최종 로그에서 `EGL_BAD_MATCH`, `Fatal signal`, `PreconditionPanic`은 검출되지 않았다. 총 20개 분류 표시 조합에서 소품 가림·캐릭터와의 겹침을 확인하지 못했고, 동일 화면 크기에서 기존 캐릭터 구도·크기를 유지했다. 캡처는 `.state/props26/split-fix/run2/qa/final-{adult,parent}-{2,3}-*.png`에 로컬 보관하며 Git에는 포함하지 않는다.

`ktlintCheck`, `testDebugUnitTest`, `assembleDemo`, `assembleDebug`, `git diff --check`가 통과했다. 실제 목적지 분류 공급은 여전히 #23 연동 후 검증해야 한다. 이번 결과는 데모 입력으로 렌더링과 분할 전환을 확인한 것이며, 모든 착장 조합 또는 실제 차량에서의 검증을 뜻하지 않는다.

분할 수정 당시 별도 문제로 남겼던 프로필 관계 편집 후 텍스처 크래시의 후속 보완은 아래에 기록한다.


## 프로필 관계 편집 시 텍스처 수명 보호 (2026-11-14)

이전 로그에는 관계 편집 후 복귀 중 `asyncBeginLoad` 경로의 `Texture is not created yet` 크래시가 있었다. 다만 분할 수정 커밋 `ec66d48`에서 관계 편집·복귀를 16회 재시도했을 때는 재현되지 않았다. 따라서 아래 변경은 당시 크래시의 유일한 원인을 확정한 결과가 아니라, 소스에서 확인한 안전하지 않은 비동기 리소스 수명을 보호하는 수정이다.

SceneView 4.25.0의 기존 모델 로딩 helper는 비동기 작업 시작 직후 인스턴스를 반환하고, composition에서 빠질 때 모델을 파괴한다. 앱의 새 `rememberRequestModelInstance`는 요청마다 전용 ResourceLoader와 asset을 소유한다. 메인 스레드에서 준비 상태를 갱신하고 완료된 모델만 노드에 전달한다. 취소 시에는 해당 요청의 로딩을 취소하고 데이터·asset·loader를 정리한다. 완료된 asset은 소비 노드가 분리된 뒤 한 번만 해제한다. 비활성 SceneView에서도 준비 작업이 진행되며, 다른 모델 요청의 로딩을 취소하지 않는다.

이는 [Filament 1.77.3 ResourceLoader의 완료·취소 계약](https://github.com/google/filament/blob/v1.77.3/libs/gltfio/include/gltfio/ResourceLoader.h)과 같은 버전 ModelViewer의 cancel→evict→detach→destroy 순서를 기준으로 구현했다. 모델 파일과 외부 URI의 읽기는 IO에서 수행하고 네이티브 asset 접근과 해제는 메인 스레드에 둔다.

관계를 편집한 프로필이 이전 관계의 여행 세션 look을 잠시 재사용하지 않도록 관계와 해당 관계의 카탈로그 소속을 함께 확인한다. 같은 관계의 여러 프로필 중 유효한 저장 착장을 먼저 사용하고, 없으면 해당 관계의 기본 착장을 준비한다. 카메라 z=2.05, 모델 스케일, 홈 영역과 소품 위치는 변경하지 않았다.

검증 결과:

- `ktlintCheck`, `testDebugUnitTest`(39개, 실패 0), `assembleDemo`, `assembleDebug` 통과.
- 새 단위 테스트는 완료 전 게시 금지, 로딩 중 취소→해제 순서, 완료 후 asset 유지와 중복 해제 방지, 관계 불일치·잘못된 look·다중 프로필 캐시 선택을 확인한다.
- Pleos에서 성인→부모님→아이 관계 편집·저장·홈 복귀를 세 차례 반복(총 9회). 모든 화면에서 올바른 연령의 캐릭터가 표시됐고, 프로세스 PID가 유지됐다. 캡처에서 캐릭터 구도·크기를 확인했다.
- 관계 변경 후 아이 홈에서 2→3분할, 소품 다섯 종 전환, 3→2분할 복귀도 정상 동작했다.
- 관계 전환 로그에 `Texture is not created yet`, `PreconditionPanic`, `Fatal signal`, 새 모델 로더 오류 및 `EGL_BAD_MATCH`가 없었다.
- 로컬 근거: `.state/profile-texture/run/after-results.txt`, `after-relations-log.txt`, `after-sheet.jpg`. APK SHA-256: `e5cb9925526652aff80ab31d12e1faf14a202bde3419c94c0f3048d2e2304f3d`. 캡처·로그·APK는 Git 제외.

자동 테스트는 수명 계약을 검사하며 실제 Filament 네이티브 실행을 대체하지 않는다. 이번 반복에서 크래시는 관찰되지 않았지만 간헐적 오류가 모든 환경에서 사라졌다고 단정하지 않는다. #23의 실제 내비게이션 분류 연동은 기존과 같이 별도 범위다.
