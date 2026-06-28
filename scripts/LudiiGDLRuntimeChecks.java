import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.rng.core.source64.SplitMix64;

import game.Game;
import game.rules.play.moves.Moves;
import other.context.Context;
import other.move.Move;
import other.trial.Trial;

public final class LudiiGDLRuntimeChecks
{
	private LudiiGDLRuntimeChecks()
	{
	}

	public static CheckResult run(final Game game, final Config config)
	{
		final CheckResult result = new CheckResult();

		if (config.smoke)
			runSmokeCheck(game, config, result);

		for (int i = 0; i < config.playouts; i++)
			runPlayoutCheck(game, config, i, result);

		return result;
	}

	private static void runSmokeCheck(final Game game, final Config config, final CheckResult result)
	{
		try
		{
			final Context context = new Context(game, new Trial(game));
			seedContext(context, config.seed);
			game.start(context);

			final Moves legalMoves = game.moves(context);
			if (legalMoves == null)
			{
				result.errors.add("Smoke check: game.moves(context) returned null after game.start(context).");
				return;
			}
			if (legalMoves.moves() == null)
			{
				result.errors.add("Smoke check: legalMoves.moves() returned null after game.start(context).");
				return;
			}

			final int initialLegalMoves = legalMoves.moves().size();
			result.notes.add("Smoke check: initial legal moves = " + initialLegalMoves + ".");
			if (config.expectedInitialLegalMin >= 0 && initialLegalMoves < config.expectedInitialLegalMin)
			{
				result.errors.add
				(
					"Smoke check: expected at least " + config.expectedInitialLegalMin
					+ " initial legal moves, found " + initialLegalMoves + "."
				);
			}

			if (config.applyInitialMove && initialLegalMoves > 0)
			{
				final int beforeMoves = context.trial().numMoves();
				final Move applied = game.apply(context, legalMoves.moves().get(0));
				if (applied == null)
					result.errors.add("Smoke check: applying the first legal move returned null.");
				if (context.trial().numMoves() <= beforeMoves)
					result.errors.add("Smoke check: applying the first legal move did not advance the trial.");

				final Moves nextLegalMoves = game.moves(context);
				if (nextLegalMoves == null || nextLegalMoves.moves() == null)
					result.errors.add("Smoke check: legal move generation failed after applying the first legal move.");
				else
					result.notes.add("Smoke check: legal moves after first move = " + nextLegalMoves.moves().size() + ".");
			}
		}
		catch (final Throwable t)
		{
			result.errors.add("Smoke check: " + cleanMessage(t));
		}
	}

	private static void runPlayoutCheck
	(
		final Game game,
		final Config config,
		final int index,
		final CheckResult result
	)
	{
		final int playoutNumber = index + 1;
		try
		{
			final Context context = new Context(game, new Trial(game));
			seedContext(context, config.seed + index);
			game.start(context);

			final Trial trial = game.playout
					(
						context,
						null,
						1.0,
						null,
						0,
						config.maxActions,
						new Random(config.seed + index)
					);

			if (trial == null)
			{
				result.errors.add("Playout " + playoutNumber + ": game.playout(...) returned null.");
				return;
			}

			final String summary = "Playout " + playoutNumber + ": moves = " + trial.numMoves()
				+ ", terminal = " + trial.over() + ".";
			result.notes.add(summary);

			if (!trial.over() && config.maxActions >= 0)
			{
				final String message = "Playout " + playoutNumber
					+ ": reached --max-actions " + config.maxActions + " before a terminal state.";
				if (config.requireTerminal)
					result.errors.add(message);
				else
					result.warnings.add(message);
			}
		}
		catch (final Throwable t)
		{
			result.errors.add("Playout " + playoutNumber + ": " + cleanMessage(t));
		}
	}

	private static void seedContext(final Context context, final long seed) throws ReflectiveOperationException
	{
		final Field rngField = Context.class.getDeclaredField("rng");
		rngField.setAccessible(true);
		rngField.set(context, new SplitMix64(Long.valueOf(seed)));
	}

	private static String cleanMessage(final Throwable throwable)
	{
		String message = throwable.getMessage();
		if (message == null || message.trim().isEmpty())
			message = throwable.toString();

		message = message.replaceAll("(?s)<[^>]*>", " ");
		message = message.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">");
		message = message.replace("&amp;", "&");
		message = message.replaceAll("[\\t ]+", " ");
		message = message.replaceAll(" *\\R *", "\n");
		return message.trim();
	}

	public static final class Config
	{
		boolean smoke;
		boolean applyInitialMove = true;
		boolean requireTerminal;
		int expectedInitialLegalMin = -1;
		int playouts;
		int maxActions = 300;
		long seed = 1L;

		public boolean enabled()
		{
			return smoke || playouts > 0;
		}
	}

	public static final class CheckResult
	{
		public final List<String> errors = new ArrayList<String>();
		public final List<String> warnings = new ArrayList<String>();
		public final List<String> notes = new ArrayList<String>();
	}
}
