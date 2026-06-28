import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import compiler.Compiler;
import game.Game;
import game.match.Subgame;
import main.grammar.Description;
import main.grammar.Report;
import main.options.Ruleset;
import main.options.UserSelections;
import other.GameLoader;

public final class ValidateLudiiGDL
{
	private static final int EXIT_USAGE = 2;
	private static final int EXIT_INVALID = 1;

	private ValidateLudiiGDL()
	{
	}

	public static void main(final String[] args) throws Exception
	{
		final Config config;
		try
		{
			config = Config.parse(args);
		}
		catch (final IllegalArgumentException e)
		{
			System.err.println(e.getMessage());
			printUsage();
			System.exit(EXIT_USAGE);
			return;
		}
		if (config.help || config.files.isEmpty())
		{
			printUsage();
			System.exit(config.help ? 0 : EXIT_USAGE);
		}

		boolean anyInvalid = false;
		for (int i = 0; i < config.files.size(); i++)
		{
			final ValidationResult result = validate(config.files.get(i), config);
			if (i > 0)
				System.out.println();
			result.print(config.failOnWarning);
			anyInvalid |= !result.valid(config.failOnWarning);
		}

		System.exit(anyInvalid ? EXIT_INVALID : 0);
	}

	private static ValidationResult validate(final File file, final Config config)
	{
		final ValidationResult result = new ValidationResult(file);
		if (!file.isFile())
		{
			result.errors.add("File does not exist or is not a regular file.");
			return result;
		}

		try
		{
			final String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			final List<String> selectedOptions = new ArrayList<String>(config.options);
			if (config.rulesetName != null)
				selectedOptions.addAll(0, rulesetOptions(raw, file, config.rulesetName, config));

			final Report report = new Report();
			final Game game = compile(raw, file, selectedOptions, report, config);
			result.gameName = game.name();
			result.options.addAll(selectedOptions);
			result.errors.addAll(report.errors());
			result.warnings.addAll(report.warnings());
			result.notes.addAll(report.notes());
			result.requirements.addAll(game.requirementReport());
			result.crashes.addAll(game.crashReport());

			if (game.hasSubgames() && game.instances() != null)
				compileSubgames(game);

			if (config.runtime.enabled())
			{
				final LudiiGDLRuntimeChecks.CheckResult checks = LudiiGDLRuntimeChecks.run(game, config.runtime);
				result.errors.addAll(checks.errors);
				result.warnings.addAll(checks.warnings);
				result.notes.addAll(checks.notes);
			}
		}
		catch (final Throwable t)
		{
			result.errors.add(cleanMessage(t));
		}

		return result;
	}

	private static Game compile
	(
		final String raw,
		final File file,
		final List<String> options,
		final Report report,
		final Config config
	)
	{
		final Description description = new Description(raw);
		description.setFilePath(file.getAbsolutePath());

		if (config.verboseLudii)
		{
			return (Game)Compiler.compile(description, new UserSelections(options), report, false);
		}

		final PrintStream originalOut = System.out;
		final PrintStream originalErr = System.err;
		final ByteArrayOutputStream captured = new ByteArrayOutputStream();
		final PrintStream captureStream = new PrintStream(captured, true);
		try
		{
			System.setOut(captureStream);
			System.setErr(captureStream);
			return (Game)Compiler.compile(description, new UserSelections(options), report, false);
		}
		finally
		{
			System.setOut(originalOut);
			System.setErr(originalErr);
			captureStream.close();
		}
	}

	private static List<String> rulesetOptions
	(
		final String raw,
		final File file,
		final String rulesetName,
		final Config config
	)
	{
		final Report report = new Report();
		final Game game = compile(raw, file, new ArrayList<String>(), report, config);
		for (final Ruleset ruleset : game.description().rulesets())
			if (ruleset.heading().equals(rulesetName))
				return new ArrayList<String>(ruleset.optionSettings());

		throw new IllegalArgumentException("Ruleset not found: " + rulesetName);
	}

	private static void compileSubgames(final Game game)
	{
		for (final Subgame subgame : game.instances())
		{
			final ArrayList<String> options = new ArrayList<String>();
			if (subgame.optionName() != null)
				options.add(subgame.optionName());
			subgame.setGame(GameLoader.loadGameFromName(subgame.gameName() + ".lud", options));
		}
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

	private static void printUsage()
	{
		System.out.println("Usage: validate_ludii_gdl.sh [options] FILE.lud [FILE.lud ...]");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  --option VALUE       Select a Ludii option, e.g. \"Board Size/19x19\".");
		System.out.println("  --ruleset NAME       Validate with the named ruleset option settings.");
		System.out.println("  --smoke              Start the game, generate legal moves, and apply the first legal move.");
		System.out.println("  --no-apply-initial   Do not apply the first legal move during --smoke.");
		System.out.println("  --expect-initial-legal-min N");
		System.out.println("                       Require at least N legal moves in the initial state.");
		System.out.println("  --playouts N         Run N random playout checks.");
		System.out.println("  --max-actions N      Cap each playout at N actions; use -1 for no cap. Default: 300.");
		System.out.println("  --require-terminal   Fail when a capped playout does not reach a terminal state.");
		System.out.println("  --seed N             Seed for deterministic playout checks. Default: 1.");
		System.out.println("  --fail-on-warning    Return exit code 1 when Ludii reports warnings.");
		System.out.println("  --verbose-ludii      Let Ludii print its internal compiler output.");
		System.out.println("  -h, --help           Show this help.");
	}

	private static final class Config
	{
		private final List<File> files = new ArrayList<File>();
		private final List<String> options = new ArrayList<String>();
		private final LudiiGDLRuntimeChecks.Config runtime = new LudiiGDLRuntimeChecks.Config();
		private String rulesetName;
		private boolean failOnWarning;
		private boolean verboseLudii;
		private boolean help;

		private static Config parse(final String[] args)
		{
			final Config config = new Config();
			for (int i = 0; i < args.length; i++)
			{
				final String arg = args[i];
				if ("-h".equals(arg) || "--help".equals(arg))
				{
					config.help = true;
				}
				else if ("--option".equals(arg))
				{
					config.options.add(requireValue(args, ++i, arg));
				}
				else if ("--ruleset".equals(arg))
				{
					config.rulesetName = requireValue(args, ++i, arg);
				}
				else if ("--smoke".equals(arg))
				{
					config.runtime.smoke = true;
				}
				else if ("--no-apply-initial".equals(arg))
				{
					config.runtime.applyInitialMove = false;
				}
				else if ("--expect-initial-legal-min".equals(arg))
				{
					config.runtime.smoke = true;
					config.runtime.expectedInitialLegalMin = parseInt(requireValue(args, ++i, arg), arg);
				}
				else if ("--playouts".equals(arg))
				{
					config.runtime.playouts = parseNonNegativeInt(requireValue(args, ++i, arg), arg);
				}
				else if ("--max-actions".equals(arg))
				{
					config.runtime.maxActions = parseInt(requireValue(args, ++i, arg), arg);
					if (config.runtime.maxActions < -1)
						throw new IllegalArgumentException("--max-actions must be -1 or greater.");
				}
				else if ("--require-terminal".equals(arg))
				{
					config.runtime.requireTerminal = true;
				}
				else if ("--seed".equals(arg))
				{
					config.runtime.seed = parseLong(requireValue(args, ++i, arg), arg);
				}
				else if ("--fail-on-warning".equals(arg))
				{
					config.failOnWarning = true;
				}
				else if ("--verbose-ludii".equals(arg))
				{
					config.verboseLudii = true;
				}
				else if (arg.startsWith("-"))
				{
					throw new IllegalArgumentException("Unknown option: " + arg);
				}
				else
				{
					config.files.add(new File(arg));
				}
			}
			return config;
		}

		private static String requireValue(final String[] args, final int index, final String option)
		{
			if (index >= args.length)
				throw new IllegalArgumentException("Missing value for " + option);
			return args[index];
		}

		private static int parseNonNegativeInt(final String value, final String option)
		{
			final int parsed = parseInt(value, option);
			if (parsed < 0)
				throw new IllegalArgumentException(option + " must be non-negative.");
			return parsed;
		}

		private static int parseInt(final String value, final String option)
		{
			try
			{
				return Integer.parseInt(value);
			}
			catch (final NumberFormatException e)
			{
				throw new IllegalArgumentException("Invalid integer for " + option + ": " + value);
			}
		}

		private static long parseLong(final String value, final String option)
		{
			try
			{
				return Long.parseLong(value);
			}
			catch (final NumberFormatException e)
			{
				throw new IllegalArgumentException("Invalid integer for " + option + ": " + value);
			}
		}
	}

	private static final class ValidationResult
	{
		private final File file;
		private final List<String> errors = new ArrayList<String>();
		private final List<String> warnings = new ArrayList<String>();
		private final List<String> notes = new ArrayList<String>();
		private final List<String> requirements = new ArrayList<String>();
		private final List<String> crashes = new ArrayList<String>();
		private final List<String> options = new ArrayList<String>();
		private String gameName;

		private ValidationResult(final File file)
		{
			this.file = file;
		}

		private boolean valid(final boolean failOnWarning)
		{
			return errors.isEmpty()
				&& crashes.isEmpty()
				&& requirements.isEmpty()
				&& (!failOnWarning || warnings.isEmpty());
		}

		private void print(final boolean failOnWarning)
		{
			final boolean ok = valid(failOnWarning);
			System.out.println((ok ? "OK: " : "INVALID: ") + file.getPath());
			if (gameName != null)
				System.out.println("Game: " + gameName);
			if (!options.isEmpty())
				System.out.println("Options: " + options);
			printSection("Errors", errors);
			printSection("Crash risks", crashes);
			printSection("Missing requirements", requirements);
			printSection("Warnings", warnings);
			printSection("Notes", notes);
		}

		private static void printSection(final String heading, final List<String> lines)
		{
			if (lines.isEmpty())
				return;
			System.out.println(heading + ":");
			for (final String line : lines)
				System.out.println("  - " + line.replace("\n", "\n    "));
		}
	}
}
