import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

import org.apache.commons.rng.core.source64.SplitMix64;

import compiler.Compiler;
import game.Game;
import main.collections.FastArrayList;
import main.grammar.Description;
import main.grammar.Report;
import main.options.UserSelections;
import other.context.Context;
import other.move.Move;
import other.trial.Trial;

public final class InspectCetkaikMoves
{
	private InspectCetkaikMoves()
	{
	}

	public static void main(final String[] args) throws Exception
	{
		if (args.length < 1)
		{
			System.err.println("Usage: InspectCetkaikMoves FILE.lud [passes]");
			System.exit(2);
		}

		final int passes = args.length >= 2 ? Integer.parseInt(args[1]) : 0;
		final Game game = compile(new File(args[0]));
		final Context context = new Context(game, new Trial(game));
		for (int i = 2; i < args.length; i++)
		{
			if (args[i].startsWith("seed="))
				seedContext(context, Long.parseLong(args[i].substring("seed=".length())));
		}
		game.start(context);

		for (int i = 0; i < passes; i++)
		{
			final FastArrayList<Move> legal = game.moves(context).moves();
			Move pass = null;
			for (int j = 0; j < legal.size(); j++)
			{
				if (legal.get(j).isPass())
				{
					pass = legal.get(j);
					break;
				}
			}
			if (pass == null)
				throw new IllegalStateException("No pass move at step " + i + "; legal moves=" + legal.size());
			game.apply(context, pass);
		}

		for (int i = 2; i < args.length; i++)
		{
			if (args[i].startsWith("seed="))
				continue;
			if ("forceP1".equals(args[i]))
			{
				forceStarter(context, 1);
				System.out.println("APPLY forceP1");
				continue;
			}
			if ("forceP2".equals(args[i]))
			{
				forceStarter(context, 2);
				System.out.println("APPLY forceP2");
				continue;
			}
			final FastArrayList<Move> legal = game.moves(context).moves();
			final int moveIndex = findMoveIndex(context, legal, args[i]);
			if (moveIndex < 0 || moveIndex >= legal.size())
				throw new IllegalArgumentException("Move not found: " + args[i] + ", legal moves=" + legal.size());
			System.out.println("APPLY " + args[i] + " -> " + legal.get(moveIndex).toTurnFormat(context, true));
			game.apply(context, legal.get(moveIndex));
		}

		final FastArrayList<Move> legal = game.moves(context).moves();
		System.out.println("mover=" + context.state().mover());
		System.out.println("trialMoves=" + context.trial().numMoves());
		System.out.println("over=" + context.trial().over());
		System.out.println("winner=" + (context.trial().status() == null ? "none" : context.trial().status().winner()));
		System.out.println("StarterChosen=" + context.state().getValue("StarterChosen"));
		System.out.println("StarterStage=" + context.state().getValue("StarterStage"));
		System.out.println("StepOverPending=" + context.state().getValue("StepOverPending"));
		System.out.println("StepOverOrigin=" + context.state().getValue("StepOverOrigin"));
		System.out.println("StepOverVia=" + context.state().getValue("StepOverVia"));
		System.out.println("DeclarationPending=" + context.state().getValue("DeclarationPending"));
		System.out.println("scores=" + context.score(1) + "," + context.score(2));
		System.out.println("legalMoves=" + legal.size());
		for (int i = 0; i < legal.size(); i++)
		{
			final Move move = legal.get(i);
			System.out.println("MOVE " + i + ": " + move.toTrialFormat(context));
			System.out.println("  turn=" + move.toTurnFormat(context, true));
			System.out.println("  from=" + move.from() + " to=" + move.to() + " pass=" + move.isPass());
			System.out.println("  actions=" + move.actions().size());
			for (int j = 0; j < move.actions().size(); j++)
			{
				System.out.println("    " + j + ": " + move.actions().get(j).toString());
			}
		}
	}

	private static void seedContext(final Context context, final long seed) throws ReflectiveOperationException
	{
		final Field rngField = Context.class.getDeclaredField("rng");
		rngField.setAccessible(true);
		rngField.set(context, new SplitMix64(Long.valueOf(seed)));
	}

	private static int findMoveIndex(final Context context, final FastArrayList<Move> legal, final String spec)
	{
		if (spec.matches("-?\\d+"))
			return Integer.parseInt(spec);
		for (int i = 0; i < legal.size(); i++)
		{
			final Move move = legal.get(i);
			if ("Pass".equalsIgnoreCase(spec) && move.isPass())
				return i;
			final String turn = move.toTurnFormat(context, true);
			final String trial = move.toTrialFormat(context);
			if (turn.equals(spec) || trial.equals(spec) || turn.contains(spec) || trial.contains(spec))
				return i;
		}
		return -1;
	}

	private static void forceStarter(final Context context, final int player)
	{
		forceStarterContext(context, player);
		if (context.currentInstanceContext() != context)
			forceStarterContext(context.currentInstanceContext(), player);
	}

	private static void forceStarterContext(final Context context, final int player)
	{
		context.state().setValue("StarterChosen", 1);
		context.state().setValue("StarterStage", 0);
		context.state().setMover(player);
		context.state().setNext(player);
	}

	private static Game compile(final File file) throws Exception
	{
		final String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		final Description description = new Description(raw);
		description.setFilePath(file.getAbsolutePath());
		final Report report = new Report();
		final Game game = (Game) Compiler.compile(description, new UserSelections(new ArrayList<String>()), report, false);
		if (!report.errors().isEmpty())
			throw new IllegalStateException(report.errors().toString());
		return game;
	}
}
