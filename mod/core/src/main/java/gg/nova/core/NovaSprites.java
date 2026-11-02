package gg.nova.core;

/**
 * Pixel-Icons des Nova-Designs (Vorlage: Icon-Sheet "person … gear").
 * Farben: k Kontur, w Weiß, b Blau, m Dunkelblau, g Grau, G Grün, y Gelb, r Rot.
 */
public final class NovaSprites {
	private static final String KEYS = "kwbmgGyr";
	private static final int[] PALETTE = {
		0xFF121217, 0xFFF4F4FB, 0xFF6A6A72, 0xFF47474F, 0xFFC2C2CB, 0xFF3FBF5A, 0xFFE0A33A, 0xFFE05555,
	};

	private NovaSprites() {
	}

	private static Sprite icon(String... rows) {
		return new Sprite(rows, KEYS, PALETTE);
	}

	/** Einzelspieler. */
	public static final Sprite PERSON = icon(
		"....kkkkkkk.....",
		"....kwwwwwk.....",
		"....kwkwkwk.....",
		"....kwwwwwk.....",
		"....kgggggk.....",
		"..kkkkkkkkkkk...",
		"..kwkbbbbbkwk...",
		"..kwkbbbbbkwk...",
		"..kwkbbbbbkwk...",
		"..kkkbbbbbkkk...",
		"....kmmmmmk.....",
		"....kmmkmmk.....",
		"....kmmkmmk.....",
		"....kkk.kkk....."
	);

	/** Mehrspieler. */
	public static final Sprite PEOPLE = icon(
		"..........kkkkk.",
		"..kkkkkk..kbbbk.",
		".kwwwwwwk.kbbbk.",
		".kwwwwwwk.kbbbk.",
		".kwwwwwwk.kkkkk.",
		"..kwwwwk.kbbbbbk",
		"...kkkk..kbbbbbk",
		".kkwwwwkkkbbbbbk",
		"kwwwwwwwwkbbbbbk",
		"kwwwwwwwwkmmmmmk",
		"kwwwwwwwwkmmmmmk",
		"kggggggggkkkkkkk",
		"kkkkkkkkkk......"
	);

	/** Nova Server. */
	public static final Sprite SERVER = icon(
		"kkkkkkkkkkkkkkkk",
		"kwwwwwwwwwwwwwwk",
		"kwGkggggggggggwk",
		"kwwwwwwwwwwwwwwk",
		"kkkkkkkkkkkkkkkk",
		"kwwwwwwwwwwwwwwk",
		"kwGkggggggggggwk",
		"kwwwwwwwwwwwwwwk",
		"kkkkkkkkkkkkkkkk",
		"kwwwwwwwwwwwwwwk",
		"kwykggggggggggwk",
		"kwwwwwwwwwwwwwwk",
		"kkkkkkkkkkkkkkkk"
	);

	/** Sprache. */
	public static final Sprite GLOBE = icon(
		"....kkkkkk....",
		"..kkbwbbwbkk..",
		".kbbbwbbwbbbk.",
		".kwwwwwwwwwwk.",
		"kbbbbwbbwbbbbk",
		"kbbbbwbbwbbbbk",
		"kwwwwwwwwwwwwk",
		"kbbbbwbbwbbbbk",
		"kbbbbwbbwbbbbk",
		".kwwwwwwwwwwk.",
		".kmmmwmmwmmmk.",
		"..kkmwmmwmkk..",
		"....kkkkkk...."
	);

	/** Barrierefreiheit. */
	public static final Sprite ACCESS = icon(
		"....kkkkkk....",
		"..kkbbbbbbkk..",
		".kbbbbwwbbbbk.",
		".kbbbbwwbbbbk.",
		"kbwwwwwwwwwwbk",
		"kbbbbwwwwbbbbk",
		"kbbbbwwwwbbbbk",
		"kbbbbwbbwbbbbk",
		"kbbbwwbbwwbbbk",
		".kmmmmmmmmmmk.",
		".kmmmmmmmmmmk.",
		"..kkmmmmmmkk..",
		"....kkkkkk...."
	);

	/** Mods. */
	public static final Sprite MODS = icon(
		"kkkkkk..kkkkkk",
		"kwwwwk..kbbbbk",
		"kwggwk..kbGGbk",
		"kwggwk..kbGGbk",
		"kwwwwk..kbbbbk",
		"kkkkkk..kkkkkk",
		"..............",
		"kkkkkk..kkkkkk",
		"kbbbbk..kwwwwk",
		"kbwwbk..kwggwk",
		"kbwwbk..kwggwk",
		"kmmmmk..kwwwwk",
		"kkkkkk..kkkkkk"
	);

	/** Cosmetics. */
	public static final Sprite CAPE = icon(
		"..kkkk..kkkk..",
		".kwwwwkkwwwwk.",
		"kwwwwwwwwwwwwk",
		"kwwwwwwwwwwwwk",
		"kkkwwwwbwwwkkk",
		"..kwwwbbbwwk..",
		"..kwwwwbwwwk..",
		"..kwwwwwwwwk..",
		"..kggggggggk..",
		"..kkkkkkkkkk.."
	);

	/** Screenshots. */
	public static final Sprite CAM = icon(
		"...kkkkk........",
		"kkkkkkkkkkkkkkkk",
		"kwwwwwwwwwwwwrwk",
		"kwwwkkkkkkwwwwwk",
		"kwwkbbbbbbkwwwwk",
		"kwwkbwbbbbkwwwwk",
		"kwwkbbbbmbkwwwwk",
		"kwwwkkkkkkwwwwwk",
		"kggggggggggggggk",
		"kkkkkkkkkkkkkkkk"
	);

	/** Optionen. */
	public static final Sprite GEAR = icon(
		"......kkkk......",
		"...kk.kwwk.kk...",
		"..kwwkkwwkkwwk..",
		"..kwwwwwwwwwwk..",
		"...kwwkkkkwwk...",
		"kkkkwkbbbbkwkkkk",
		"kwwwwkbmmbkwwwwk",
		"kggggkbmmbkggggk",
		"kkkkgkbbbbkgkkkk",
		"...kggkkkkggk...",
		"..kggggggggggk..",
		"..kggkkggkkggk..",
		"...kk.kggk.kk...",
		"......kkkk......"
	);

	/** Isometrischer Grasblock mit Schnee (12 × 12) – Lade-Indikator. */
	public static final Sprite SNOW_BLOCK = new Sprite(new String[] {
		".....ww.....",
		"...wwwwww...",
		".wwwwwwwwww.",
		"wwwwwwwwwwww",
		"sswwwwwwwwtt",
		"dsssswwttttD",
		"ddddssttDDDD",
		"ddddddDDDDDD",
		"ddddddDDDDDD",
		".dddddDDDDD.",
		"...dddDDD...",
		".....dD.....",
	}, "wstdD", new int[] {0xFFFFFFFF, 0xFFE0E0E9, 0xFFC7C7CF, 0xFF8B6139, 0xFF5C3D24});
}
