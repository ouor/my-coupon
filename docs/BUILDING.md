# 빌드와 구조

## 준비

- JDK 17, Android SDK (compileSdk 37). Android Studio를 쓰면 함께 설치돼요.
- OCR 모델(ONNX)은 저장소에 들어 있고, 빌드할 때 `gradle/ocr-models.sha256`과 해시를 맞춰 봐요. 모델 출처와 변환 방법은 [core/ocr-runtime.md](core/ocr-runtime.md)에 있어요.

## 빌드

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew check
```

`check`는 lint, 단위 시험, Python 기준 구현과의 골든 일치 시험, 골든 최신성 검사를 함께 돌려요.

릴리즈 APK는 서명 키가 있어야 만들어져요. 키 파일과 비밀번호는 저장소에 없고, 규칙은 [../signing/SIGNING.md](../signing/SIGNING.md)에 있어요.

```bash
./gradlew :app:assembleRelease
```

## 구조

| 경로 | 내용 |
|---|---|
| `app/` | 안드로이드 앱 (Compose UI, Room DB, 알림, 위젯, 타일, 백업) |
| `ocr-core/` | 순수 Kotlin 인식 후처리·필드 추출 (`ocr-lab/extract.py`를 옮긴 것) |
| `ocr-engine/` | PP-OCRv5 ONNX 실행(검출·인식)과 ZXing 바코드 해독 |
| `ocr-lab/` | 인식 규칙의 Python 기준 구현, 채점·골든 생성 스크립트 |
| `tools/` | 벤치마크, 모델 변환 스크립트 |
| `app/schemas/` | Room 스키마 기록. 버전을 올릴 때마다 마이그레이션 시험을 함께 넣어요 |
| `docs/` | 기획, 디자인, 인식, 코어·UI 명세, QA, 리뷰 기록 |

인식 규칙을 바꿀 때는 먼저 `ocr-lab/`에서 고치고 골든을 다시 만든 뒤 Kotlin(`ocr-core/`)에 옮겨요. 자세한 계약은 [ocr/porting-spec.md](ocr/porting-spec.md)에 있어요.

시험용 쿠폰 이미지와 정답 라벨은 실제 바코드가 들어 있어서 저장소에 올리지 않아요(`.test/`).
