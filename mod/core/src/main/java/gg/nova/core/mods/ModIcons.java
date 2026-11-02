package gg.nova.core.mods;

import gg.nova.core.Sprite;

/** Pixel-Icons der Module (12 × 12, im Menü doppelt groß). w Weiß, g Grau, G Grün, c Türkis, r Rot, y Gelb, b Blau, d Dunkelgrau. */
final class ModIcons {
	private static final String KEYS = "wgGcrybd";
	private static final int[] PALETTE = {
		0xFFF4F4FB, 0xFF8A8A92, 0xFF5FD35F, 0xFF5FE1EA, 0xFFE05555, 0xFFF5A623, 0xFF6F7BF5, 0xFF3A3A42,
	};

	private ModIcons() {
	}

	private static Sprite icon(String... rows) {
		return new Sprite(rows, KEYS, PALETTE);
	}

	static final Sprite KEYSTROKES = icon(
		"....wwww....",
		"....wddw....",
		"....wddw....",
		"....wwww....",
		"............",
		"wwww.wwww.ww",
		"wddw.wddw.wd",
		"wddw.wddw.wd",
		"wwww.wwww.ww",
		"............",
		"............",
		"............"
	);

	static final Sprite CPS = icon(
		"............",
		".wwww..wwww.",
		"ww....ww....",
		"ww....ww....",
		"ww.....www..",
		"ww.......ww.",
		"ww.......ww.",
		".wwww.wwww..",
		"............",
		"............",
		"............",
		"............"
	);

	static final Sprite FPS = icon(
		"............",
		"G...........",
		"G...........",
		"G......G....",
		"GG....GG....",
		"G.G..G.G..G.",
		"G..GG...G.G.",
		"G........G..",
		"GGGGGGGGGGGG",
		"............",
		"............",
		"............"
	);

	static final Sprite ARMOR = icon(
		"............",
		"cc.cccc.cc..",
		"cccccccccc..",
		"cccccccccc..",
		".cccccccc...",
		".cccccccc...",
		".cccccccc...",
		".ccc..ccc...",
		".ccc..ccc...",
		"............",
		"............",
		"............"
	);

	static final Sprite EFFECTS = icon(
		"....ww......",
		"....ww......",
		"...wwww.....",
		"..wwwwww....",
		".wrrrrrrw...",
		".wrrrrrrw...",
		".wrrrwrrw...",
		".wrrrrrrw...",
		"..wwwwww....",
		"............",
		"............",
		"............"
	);

	static final Sprite COORDS = icon(
		".....w......",
		"....www.....",
		".....w......",
		"..w..w..w...",
		".ww.www.ww..",
		"..w..w..w...",
		".....w......",
		"....www.....",
		".....w......",
		"............",
		"............",
		"............"
	);

	static final Sprite ZOOM = icon(
		".wwww.......",
		"w....w......",
		"w....w......",
		"w....w......",
		"w....w......",
		".wwwwww.....",
		"......ww....",
		".......ww...",
		"........ww..",
		"............",
		"............",
		"............"
	);

	static final Sprite SPRINT = icon(
		".....ww.....",
		".....ww.....",
		"....www.....",
		"...wwwww....",
		"..w.www.w...",
		"....w.w.....",
		"...w...w....",
		"..w.....w...",
		".w.......w..",
		"............",
		"............",
		"............"
	);

	static final Sprite FULLBRIGHT = icon(
		".....y......",
		"..y..y..y...",
		"...yyyyy....",
		"...yyyyy....",
		"yyyyyyyyyyy.",
		"...yyyyy....",
		"...yyyyy....",
		"..y..y..y...",
		".....y......",
		"............",
		"............",
		"............"
	);

	static final Sprite CLOCK = icon(
		"...yyyyy....",
		"..y.....y...",
		".y...w...y..",
		".y...w...y..",
		".y...www.y..",
		".y.......y..",
		"..y.....y...",
		"...yyyyy....",
		"............",
		"............",
		"............",
		"............"
	);

	static final Sprite PING = icon(
		"............",
		".........GG.",
		".........GG.",
		"......GG.GG.",
		"......GG.GG.",
		"...GG.GG.GG.",
		"...GG.GG.GG.",
		"GG.GG.GG.GG.",
		"GG.GG.GG.GG.",
		"............",
		"............",
		"............"
	);

	static final Sprite INVENTORY = icon(
		"wwwwwwwwwww.",
		"w..w...w..w.",
		"w..w...w..w.",
		"wwwwwwwwwww.",
		"w..w...w..w.",
		"w..w...w..w.",
		"wwwwwwwwwww.",
		"w..w...w..w.",
		"wwwwwwwwwww.",
		"............",
		"............",
		"............"
	);

	static final Sprite CLOUDS = icon(
		"............",
		"....www.....",
		"..wwwwwww...",
		".wwwwwwwww..",
		"wwwwwwwwwww.",
		"............",
		".r.......r..",
		"..r.....r...",
		"...r...r....",
		"............",
		"............",
		"............"
	);

	static final Sprite PARTICLES = icon(
		"..b.....b...",
		"............",
		".....b......",
		"b.......b...",
		"...b........",
		"......b...b.",
		"..b.........",
		"........b...",
		"....b.......",
		"............",
		"............",
		"............"
	);

	static final Sprite SHADOWS = icon(
		"....www.....",
		"....www.....",
		".....w......",
		"...wwwww....",
		".....w......",
		"....w.w.....",
		"...w...w....",
		"..ddddddd...",
		".ddddddddd..",
		"............",
		"............",
		"............"
	);
}
