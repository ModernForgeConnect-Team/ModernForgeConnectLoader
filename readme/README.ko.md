# Modern Forge Connect Loader

[English](../README.md) · [简体中文](README.zh-CN.md) · [日本語](README.ja.md) · [Русский](README.ru.md) · [Deutsch](README.de.md) · [한국어](README.ko.md)

Modern Forge Connect Loader(MFCL)는 Forge 1.20.1 / Java 17 호스트 안에서 원본 Forge 1.7.10 모드를 실행하기 위한 실험적인 호환 런타임입니다.

## 프로젝트의 목적과 설계 목표

MFCL은 호스트와 같은 JVM 안의 격리된 클래스 로딩 영역에서 실제 구형 FML과 원본 모드 코드를 실행합니다. 브리지를 통해 지원되는 게임 객체, 레지스트리, 렌더링 및 상호작용을 현대 버전 게임에 연결하며 원본 모드 JAR은 변경하지 않습니다.

코어는 범용 메커니즘을 제공하고 모드별 동작은 선택적 어댑터에서 처리합니다. Thaumcraft 지식 시스템과 타우모미터의 동적 렌더링은 TC4 호환 계층으로 분리하고 있습니다. Developer API / MFCL Legacy Accessor(MLA)를 사용하면 현대 버전 애드온이 구형 Minecraft 클래스에 대한 컴파일 의존성 없이 구형 코드를 호출할 수 있습니다.

**아직 개발 중입니다. 초기화 성공이나 아이템 표시가 전체 게임 기능의 호환성을 뜻하지는 않습니다.**

## 실행 환경과 설치

| 구성 요소 | 현재 대상 |
| --- | --- |
| 호스트 | Minecraft 1.20.1, Forge 47.4.28 |
| Java | JDK 17 |
| 구형 런타임 | Minecraft 1.7.10, Forge 10.13.4.1614. 별도로 준비한 SRG 런타임 |
| 검증된 그래픽 환경 | Windows 11. 클라이언트와 통합 서버 |

1. Java 17로 MFCL을 빌드하거나 맞는 빌드를 준비한 후 전체 `mfcl-0.1.0.jar`을 현대 버전 게임의 `mods/`에 넣습니다.
2. 원본 구형 모드와 필수 의존성을 구해 현대 모드 루트 폴더가 아닌 `mods/1.7.10/` 바로 아래에 넣습니다.
3. 전용 구형 런타임과 검증된 입력 파일 매니페스트를 준비합니다. [테스트 환경 및 준비](../docs/testing.md)를 참고하세요. MFCL JAR과 Dependency Resolver에는 완전한 구형 Minecraft 런타임이 포함되지 않으며 자동으로 설치하지도 않습니다.
4. Forge를 실행하고 `logs/mfcl/legacy-1.7.10.json` 및 `logs/mfcl/runtime-1.7.10.json`에서 파일 검색 결과, 실제 로딩 단계와 오류를 확인합니다.

```text
<game-directory>/
  mods/
    mfcl-0.1.0.jar
    1.7.10/
      original-mod.jar
      required-legacy-dependency.jar
  mfcl/runtime/1.7.10/
    runtime-manifest.json
    minecraft-forge-1.7.10-srg.jar
    ...
```

MFCL은 해당 게임의 디렉터리를 사용하며 `mods/1.7.10/` 바로 아래의 JAR만 검사합니다. Minecraft, Forge 및 타사 모드 원본 파일은 MFCL과 함께 배포하지 않습니다.

## 구현된 호환 기능

- 격리된 구형 영역에서 실제 FML 초기화 및 지원되는 CoreMod / Access Transformer 실행.
- 현대 버전 아이템 / 블록 대리 객체 등록, metadata 및 원본 NBT 보존, 텍스처 / 번역 리소스 생성과 지원되는 설치, 드롭, 저장.
- 지원되는 구형 GUI, 글꼴, 아이템, 블록, 블록 엔티티 렌더링. 일부 실시간 아이템 렌더링과 구형 효과음.
- 월드, 플레이어 상태, 인벤토리, 레드스톤, 블록 엔티티 로직의 일부 및 지원되는 일반 작업대 조합법.
- 실험적 Developer API / MLA, 명시적 객체 변환, 진단 및 독립 의존성 도구.

| 기준 모드 | 검증된 범위 |
| --- | --- |
| NotEnoughItems 1.0.5.120 및 CodeChicken 의존성 | 실제 초기화, 인벤토리 오버레이, 검색, 원본 조합법 / 용도 화면 |
| AcademyCraft 1.0.7 및 LambdaLib 1.2.3 | 실제 초기화, 기본 콘텐츠 등록과 일부 설치 / 저장 검사 |
| Thaumcraft 4.2.3.5 및 Baubles 1.0.1.10 | 실제 초기화, 일부 렌더링 및 게임 로직 브리지, 타우모노미콘, 길게 누르는 스캔과 보상 / 저장, 효과음 |

자세한 지원 범위, 원본 입력 해시와 날짜별 검증 기록은 [기술 문서 색인](../docs/README.md)에 있습니다. 결과는 테스트한 버전과 기능에만 해당합니다.

## 호환성 제한

MFCL은 아직 완전한 Forge 1.7.10 클라이언트, 범용 OpenGL 에뮬레이터 또는 모든 구형 게임 하위 시스템의 대체 구현이 아닙니다. 임의의 모드, 사용자 정의 GUI / 렌더러, 기계, 월드 생성, 엔티티, 플레이어 능력 및 네트워크 프로토콜은 일부만 지원되거나 아직 구현되지 않았습니다. 통합 서버 테스트만으로 독립 전용 서버 호환성이 검증되지는 않습니다.

지원하지 않거나 데이터가 손실되는 변환은 명시적으로 거부합니다. 현대 버전 전용 블록 / 아이템은 구형 표현이 없을 수 있습니다. 범용 이벤트 전달과 엔티티 / 플레이어 변환은 아직 미완성입니다. 일반 작업대 지원은 신비 작업대, 도가니, 주입 등 모드별 제작 시스템의 지원을 뜻하지 않습니다.

TC4 스캔 완료 검증과 스캔 패킷 캡처에는 전용 호환 경로가 남아 있습니다. 단계별 이전 계획은 [API 및 어댑터 경계](../docs/developer-api.md)에 설명되어 있습니다. 구형 컨테이너는 구형 FML에 속합니다. 모드 목록의 통합 표시는 현대 ModList 또는 네트워크 핸드셰이크 등록과 다릅니다.

## Developer API / MLA 예제

독립 API를 `compileOnly`로 사용하세요. 애드온에 포함하거나 구형 Minecraft 클래스를 현대 런타임 클래스 경로에 추가하지 마세요.

```groovy
dependencies {
    compileOnly files('libs/mfcl-0.1.0-api.jar')
}
```

애드온 안에 공개 accessor를 선언하고 초기화 코드에서 구독합니다.

```java
@LegacyType("net.minecraft.item.ItemStack")
public interface OldStack {
    @LegacyMethod(name = "func_77946_l",
                  descriptor = "()Lnet/minecraft/item/ItemStack;")
    LegacyObject copy();
}

MfclApi.whenReady(runtime -> {
    LegacyObject stack = runtime.stack(
        new LegacyStack("minecraft:diamond", 0, 2, ""));
    LegacyObject copy = runtime.accessor(OldStack.class, stack).copy();
});
```

MLA는 정확한 구형 SRG 이름과 JVM 디스크립터를 사용합니다. 구형 참조는 내부 객체를 노출하지 않으며 하나의 런타임에 속합니다. 게임 로직은 해당 게임 스레드에서 호출해야 합니다. import, 필드 / 생성자, 변환, 수명 주기와 제한은 [컴파일 가능한 예제](../examples/legacy-accessor/) 및 [API 가이드](../docs/developer-api.md)를 참고하세요. 관리자는 `/mfclapi`로 진단 정보를 볼 수 있습니다.

## Legacy Dependency Resolver

[독립 Gradle 플러그인](../tools/legacy-dependency-resolver/)은 Gradle 의존성 해석을 재사용하며 명시적인 로컬 JAR 가져오기를 지원합니다. 입력에는 고정 SHA-256, 출처와 라이선스 / 배포 조건 기록이 필요합니다. 원격 저장소 추가, 전이 의존성 해석, 구형 JAR의 현대 런타임 클래스 경로 설치는 수행하지 않습니다.

콘텐츠 해시로 식별되는 JAR과 출처 매니페스트를 생성합니다. 설정, 실패 시 동작과 테스트는 [도구 README](../tools/legacy-dependency-resolver/README.md)를 참고하세요.

## 빌드, 테스트 및 기여

저장소 루트에서 JDK 17로 실행합니다.

```powershell
.\gradlew.bat test build
.\gradlew.bat apiJar apiSourcesJar compileApiExample
.\gradlew.bat -p tools/legacy-dependency-resolver test validatePlugins
.\gradlew.bat inspectLegacyMods "-PlegacyGameDir=C:/path/to/game"
```

전체 모드와 API 산출물은 `build/libs/`에 생성됩니다. Windows 이외의 환경에서는 `./gradlew`를 사용하세요. 외부 입력이 없으면 해당 기준 검사를 건너뛰며, 그래픽 검증에는 준비된 게임 디렉터리가 필요합니다. 준비 방법, 스모크 테스트 옵션과 격리된 빌드 출력은 [테스트 및 재현](../docs/testing.md)에 설명되어 있습니다.

기여할 때는 대상 모드와 버전, 재현 절차, 로그와 예상 동작을 적어 주세요. 범용 메커니즘은 코어에, 모드별 동작은 어댑터에 둡니다. 필요한 회귀 검증을 제공하고 원본 JAR을 유지하며 기존 스레드, 객체 소유권 및 등록 규칙을 따라 주세요.

## MPL-2.0 라이선스

MFCL은 [Mozilla Public License 2.0(MPL-2.0)](../LICENSE)으로 제공됩니다. Minecraft, Forge 및 타사 모드는 각자의 라이선스를 유지합니다. MFCL 라이선스는 해당 파일을 재배포할 권리를 부여하지 않습니다.
