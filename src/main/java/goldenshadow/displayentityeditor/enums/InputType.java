package goldenshadow.displayentityeditor.enums;

/**
 * An enum used to define what kind of value input is being expected
 */
public enum InputType {
    NAME("이름"),
    VIEW_RANGE("표시 거리"),
    DISPLAY_HEIGHT("표시 높이"),
    DISPLAY_WIDTH("표시 너비"),
    SHADOW_RADIUS("그림자 반경"),
    SHADOW_STRENGTH("그림자 농도"),
    TEXT_OPACITY("텍스트 불투명도"),
    LINE_WIDTH("줄 너비"),
    BACKGROUND_OPACITY("배경 불투명도"),
    BACKGROUND_COLOR("배경색"),
    TEXT("텍스트"),
    TEXT_APPEND("텍스트 추가"),
    GLOW_COLOR("발광색"),
    BLOCK_STATE("블록 상태");

    private final String label;

    InputType(String label) {
        this.label = label;
    }

    /** 입력 다이얼로그 제목·칸 이름에 쓰는 한국어 이름. */
    public String label() {
        return label;
    }
}
