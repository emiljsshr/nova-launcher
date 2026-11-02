package gg.nova.core.cosmetic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Teil von Molang, den Cosmetic-Animationen nutzen: Zahlen, + - * /, Klammern,
 * query.anim_time, math.sin/cos (Grad) und math.abs. Unbekannte Variablen sind 0 – wie in Molang.
 * Gleiche Regeln wie src/components/cosmetics/bedrockModel.ts im Launcher.
 */
final class Molang {
	interface Expr {
		float eval(float time);
	}

	private static final Pattern TOKEN = Pattern.compile("\\d+\\.?\\d*|[a-z_][a-z0-9_.]*|[-+*/()]");

	private final List<String> tokens = new ArrayList<String>();
	private int index;

	private Molang(String source) {
		Matcher matcher = TOKEN.matcher(source.toLowerCase(Locale.ROOT));
		while (matcher.find()) {
			tokens.add(matcher.group());
		}
	}

	static Expr compile(Object value) {
		if (value instanceof Number) {
			final float constant = ((Number) value).floatValue();
			return new Expr() {
				@Override
				public float eval(float time) {
					return constant;
				}
			};
		}
		return new Molang(String.valueOf(value)).sum();
	}

	private String peek() {
		return index < tokens.size() ? tokens.get(index) : null;
	}

	private String next() {
		if (index >= tokens.size()) {
			throw new IllegalArgumentException("Molang-Ausdruck unvollständig");
		}
		return tokens.get(index++);
	}

	private Expr sum() {
		Expr left = product();
		while ("+".equals(peek()) || "-".equals(peek())) {
			final boolean plus = "+".equals(next());
			final Expr l = left;
			final Expr r = product();
			left = new Expr() {
				@Override
				public float eval(float time) {
					return plus ? l.eval(time) + r.eval(time) : l.eval(time) - r.eval(time);
				}
			};
		}
		return left;
	}

	private Expr product() {
		Expr left = primary();
		while ("*".equals(peek()) || "/".equals(peek())) {
			final boolean times = "*".equals(next());
			final Expr l = left;
			final Expr r = primary();
			left = new Expr() {
				@Override
				public float eval(float time) {
					return times ? l.eval(time) * r.eval(time) : l.eval(time) / r.eval(time);
				}
			};
		}
		return left;
	}

	private Expr primary() {
		String token = next();
		if (token.equals("(")) {
			Expr inner = sum();
			next();
			return inner;
		}
		if (token.equals("-")) {
			final Expr operand = primary();
			return new Expr() {
				@Override
				public float eval(float time) {
					return -operand.eval(time);
				}
			};
		}
		if (Character.isDigit(token.charAt(0))) {
			return compile(Float.valueOf(token));
		}
		if (token.equals("query.anim_time") || token.equals("q.anim_time")) {
			return new Expr() {
				@Override
				public float eval(float time) {
					return time;
				}
			};
		}
		if (token.equals("math.pi")) {
			return compile((float) Math.PI);
		}
		if ((token.equals("math.sin") || token.equals("math.cos") || token.equals("math.abs")) && "(".equals(peek())) {
			next();
			final Expr argument = sum();
			next();
			final String function = token;
			return new Expr() {
				@Override
				public float eval(float time) {
					float x = argument.eval(time);
					if (function.equals("math.sin")) {
						return (float) Math.sin(Math.toRadians(x));
					}
					if (function.equals("math.cos")) {
						return (float) Math.cos(Math.toRadians(x));
					}
					return Math.abs(x);
				}
			};
		}
		return compile(0.0F);
	}
}
