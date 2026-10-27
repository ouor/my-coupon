"""Synthetic test cases for extract(): made-up text and numbers only (safe to commit).

Each case: name, input {lines, barcodes, width, height}, and the fields it must produce.
make_golden.py writes them with the full extract() output to tests/golden/ for the Kotlin port;
test_extract.py checks the asserted fields.
"""


def L(text, x0, y0, x1, y1, score=0.99):
    return {"text": text, "score": score, "poly": [[x0, y0], [x1, y0], [x1, y1], [x0, y1]]}


def BC(value, x0, y0, x1, y1, fmt="Code128"):
    return {"value": value, "format": fmt, "box": [[x0, y0], [x1, y0], [x1, y1], [x0, y1]]}


CASES = [
    ("table_card_wrapped_title", {
        "lines": [
            L("D0.m", 400, 280, 550, 560, 0.42),
            L("영화관A", 75, 746, 200, 792),
            L("콤보세트(팝콘L 1개 +음료M 2", 75, 801, 572, 852, 0.96),
            L("개)", 70, 848, 137, 908),
            L("1111 2222 3333", 244, 1108, 558, 1146),
            L("교환처", 92, 1234, 186, 1282), L("영화관A", 600, 1237, 708, 1280),
            L("유효기간", 95, 1324, 211, 1367), L("2027년 1월 5일", 466, 1326, 703, 1363, 0.98),
            L("주문번호", 93, 1409, 210, 1455), L("9999999999", 509, 1416, 703, 1450),
            L("메신저 선물하기", 301, 1524, 554, 1570),
        ],
        "barcodes": [BC("111122223333", 167, 946, 632, 1093)], "width": 800, "height": 1670,
    }, {"isCoupon": True, "brand": "영화관A", "productName": "콤보세트(팝콘L 1개 +음료M 2 개)", "type": "EXCHANGE",
        "code": "111122223333", "codeSource": "DECODED", "expiryDate": "2027-01-05", "issuer": "메신저 선물하기",
        "needsReview": {}}),

    ("brand_title_then_prefixed_title_stray_char", {
        "lines": [
            L("카페B", 355, 1082, 724, 1144),
            L("(카페BI)(ICE)바닐라라", 243, 1167, 838, 1224, 0.97),
            L("떼", 464, 1229, 613, 1292),
            L("2026.11.20까지 사용", 329, 1308, 746, 1361, 0.95),
            L("주문번호0000000000", 348, 1420, 733, 1458),
            L("4444 5555 6666", 340, 1696, 739, 1752),
            L("ShopLogo", 415, 2008, 674, 2091),
        ],
        "barcodes": [BC("444455556666", 230, 1480, 850, 1660)], "width": 1080, "height": 2157,
    }, {"isCoupon": True, "brand": "카페B", "productName": "(카페B)(ICE)바닐라라 떼", "expiryDate": "2026-11-20",
        "needsReview": {}}),

    ("amount_voucher", {
        "lines": [
            L("버거C(금액권)", 291, 45, 507, 78),
            L("[EVENT]버거C 모바일금액권 5만원권", 54, 570, 746, 619, 0.98),
            L("1234-5678-9012-3456", 230, 876, 569, 907),
            L("사용가능금액", 76, 1009, 238, 1048), L("50,000원", 545, 1006, 723, 1061),
            L("유효기간", 41, 1118, 143, 1158), L("2026-09-14 ~ 2026-12-16", 463, 1122, 754, 1153, 0.97),
            L("브랜드명", 44, 1172, 140, 1208), L("버거C(금액권)", 531, 1171, 754, 1207),
            L("이용 안내", 43, 1316, 155, 1356, 0.94),
            L("-1만원 이상 구매 시 사용 가능합니다.", 66, 1389, 712, 1420),
        ],
        "barcodes": [BC("1234567890123456", 111, 667, 687, 838)], "width": 800, "height": 1562,
    }, {"isCoupon": True, "brand": "버거C", "type": "AMOUNT", "faceValue": 50000, "expiryDate": "2026-12-16",
        "needsReview": {}}),

    ("yearless_note_date_conflict", {
        "lines": [
            L("편의점D", 60, 40, 200, 80),
            L("편의점D 500원 할인", 150, 300, 650, 350),
            L("사용기간", 40, 400, 150, 440), L("2026.08.01 ~ 2026.09.01", 400, 400, 760, 440),
            L("11112222333344445555", 200, 700, 600, 740),
            L("쿠폰 유의사항", 40, 800, 200, 840),
            L("-5,000원 이상 구매 시 사용 가능", 60, 860, 600, 890),
            L("-쿠폰 사용기간은 8/31까지 입니다.", 60, 900, 600, 930),
        ],
        "barcodes": [BC("11112222333344445555", 150, 500, 650, 690)], "width": 800, "height": 1000,
    }, {"isCoupon": True, "brand": "편의점D", "type": "EXCHANGE", "faceValue": None, "expiryDate": "2026-08-31",
        "expiryCandidates": ["2026-08-31", "2026-09-01"],
        "needsReview": {"expiryDate": ["MULTIPLE_CANDIDATES", "YEAR_INFERRED"]}}),

    ("ocr_only_code_low_conf_date", {
        "lines": [
            L("빵집E", 300, 100, 500, 150),
            L("빵집E 크루아상 세트", 200, 200, 700, 260),
            L("유효기간: 2026.12.31", 200, 300, 700, 340, 0.82),
            L("7777 8888 9999", 250, 600, 650, 640),
        ],
        "barcodes": [], "width": 900, "height": 900,
    }, {"isCoupon": True, "code": "777788889999", "codeSource": "OCR", "expiryDate": "2026-12-31",
        "needsReview": {"code": ["CODE_NOT_DECODED"], "expiryDate": ["LOW_CONFIDENCE"]}}),

    ("decoded_differs_from_print", {
        "lines": [
            L("교환처", 40, 100, 150, 140), L("분식F", 500, 100, 700, 140),
            L("분식F 떡볶이", 200, 40, 600, 90),
            L("2026.10.10까지 사용", 250, 160, 650, 200),
            L("1234 5678 9999", 250, 520, 650, 560),
        ],
        "barcodes": [BC("123456789990", 150, 300, 650, 500)], "width": 800, "height": 700,
    }, {"code": "123456789990", "needsReview": {"code": ["CODE_MISMATCH"]}}),

    ("receipt_not_coupon", {
        "lines": [
            L("카드결제승인", 436, 719, 1003, 848),
            L("거래 일시", 104, 1273, 304, 1333), L("2024-08-21 12:00:00", 826, 1278, 1329, 1326),
            L("합계", 103, 1572, 204, 1635), L("5,000원", 1142, 1569, 1338, 1640),
            L("00000000000000002345", 397, 3200, 1053, 3254),
        ],
        "barcodes": [BC("00000000000000002345", 132, 2650, 1315, 3190)], "width": 2048, "height": 3664,
    }, {"isCoupon": False, "expiryDate": None, "needsReview": {"expiryDate": ["NOT_FOUND"], "brand": ["NOT_FOUND"]}}),

    ("date_without_keyword", {
        "lines": [
            L("가게G 쿠폰", 100, 50, 500, 100),
            L("2026.05.05", 100, 200, 400, 240),
            L("2026.06.30", 100, 260, 400, 300),
            L("5555 6666 7777", 100, 600, 500, 640),
        ],
        "barcodes": [BC("555566667777", 100, 400, 500, 580)], "width": 600, "height": 700,
    }, {"isCoupon": False, "expiryDate": "2026-06-30",
        "needsReview": {"expiryDate": ["NO_KEYWORD", "MULTIPLE_CANDIDATES"], "brand": ["NOT_FOUND"]}}),

    ("sender_line", {
        "lines": [
            L("홍길동님이 보낸 선물", 100, 30, 500, 70),
            L("카페H 아메리카노", 100, 100, 500, 160),
            L("사용처", 40, 200, 150, 240), L("카페H 매장", 400, 200, 600, 240),
            L("2026.03.01까지 사용", 100, 260, 500, 300),
        ],
        "barcodes": [BC("ABC123456789", 100, 400, 500, 560)], "width": 600, "height": 700,
    }, {"brand": "카페H", "sender": "홍길동", "productName": "카페H 아메리카노",
        "needsReview": {"sender": ["ALWAYS_CHECK"]}}),
]
