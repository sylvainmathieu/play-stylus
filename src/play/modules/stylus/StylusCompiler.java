package play.modules.stylus;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;

import play.Logger;
import play.Play;
import play.libs.IO;

public class StylusCompiler {

	public static final boolean precompiling = System.getProperty("precompile") != null;
	public static final String tmpOrPrecompile = Play.usePrecompiled || precompiling ? "precompiled" : "tmp";
	public static final File baseCompiledDirectory = Play.getFile(tmpOrPrecompile +  "/stylus_cache");
	public static final File publicStylesheetsDir = Play.getFile("/public/stylesheets");

	private static final Pattern imports = Pattern.compile("@import\\s+[\"']?([^\"']+)[\"']?");

	private static void findDependencies(File stylusFile, List<File> deps) {
		try {
			if (stylusFile.exists()) {
				Matcher m = imports.matcher(IO.readContentAsString(stylusFile));
				while (m.find()) {
					String fileName = m.group(1);
					File depStylus = Play.getFile("public/stylesheets/" + fileName + (fileName.endsWith(".styl") ? "" : ".styl"));
					if (depStylus.exists()) {
						deps.add(depStylus);
						findDependencies(depStylus, deps);
					}
				}
			}
		} catch (Exception e) {
			Logger.error(e, "in StylusCompiler.findDependencies");
		}
	}

	public static long lastModified(File stylusFile) {
		List<File> deps = new ArrayList<File>();
		findDependencies(stylusFile, deps);
		Long lastModified = stylusFile.lastModified();
		for (File dep : deps) {
			if (lastModified < dep.lastModified()) {
				lastModified = dep.lastModified();
			}
		}
		return lastModified;
	}

	public static File getCompiledFile(File stylusFile) {
		return new File(baseCompiledDirectory,
				stylusFile.getAbsolutePath()
				.replace(publicStylesheetsDir.getAbsolutePath() + "/", "")
				.replace(".styl", ".css"));
	}

	public static String compile(File stylusFile) {
		return compile(stylusFile, false);
	}

	public static String compile(File stylusFile, boolean force) {
		try {
			File compiledFile = getCompiledFile(stylusFile);
			if (!force && compiledFile.exists() && compiledFile.lastModified() > lastModified(stylusFile)) {
				return IO.readContentAsString(compiledFile);
			}
			else {
				String stylusContent = IO.readContentAsString(stylusFile);
				String compiled = compileProcess(stylusContent);
				if (!compiledFile.exists()) {
					if (!compiledFile.getParentFile().exists()) {
						compiledFile.getParentFile().mkdirs();
					}
					compiledFile.createNewFile();
				}
				IO.writeContent(compiled, compiledFile);
				return compiled;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static String getCompiled(String styleName) {
		return IO.readContentAsString(new File(baseCompiledDirectory, styleName + ".styl"));
	}

	public static void compileAll() {

		try {
			FileUtils.deleteDirectory(baseCompiledDirectory);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		baseCompiledDirectory.mkdirs();

		Collection<File> stylusFiles = FileUtils.listFiles(publicStylesheetsDir,
			new RegexFileFilter(".+\\.styl$"),
			DirectoryFileFilter.DIRECTORY);

		Logger.info("Compile all stylus files...");
		for (File stylusFile : stylusFiles) {
			compile(stylusFile, true);
			Logger.info("%s compiled", stylusFile.getAbsoluteFile());
		}
		Logger.info("Done.");
	}

	public static String compileStyle(String styleName) {
		File stylusFile = new File(publicStylesheetsDir, styleName + ".styl");
		File cssFile = new File(publicStylesheetsDir, styleName + ".css");
		String compiledCss = "";
		if (stylusFile.exists()) {
			compiledCss = compile(stylusFile);
		} else if (cssFile.exists()) {
			compiledCss = IO.readContentAsString(cssFile);
		}
		return compiledCss;
	}

	public static String compileProcess(String stylusContent) {
		String compiled = "";
		String coffeeNativeFullpath = Play.configuration.getProperty("stylus.path", "");
		List<String> command = new ArrayList<String>();
		command.add(coffeeNativeFullpath);
		command.add("--include-css");
		command.add("--include");
		command.add(Play.applicationPath + "/public/stylesheets");
		if (Play.mode.isProd()) {
			command.add("-c");
		}
		ProcessBuilder pb = new ProcessBuilder(command);
		Process stylusProcess = null;
		try {
			stylusProcess = pb.start();
			OutputStream os = stylusProcess.getOutputStream();
			os.write(stylusContent.getBytes());
			os.flush();
			os.close();
			BufferedReader minifyReader = new BufferedReader(new InputStreamReader(stylusProcess.getInputStream()));
			String line;
			while ((line = minifyReader.readLine()) != null) {
				compiled += line + "\n";
			}
			String processErrors = "";
			BufferedReader errorReader = new BufferedReader(new InputStreamReader(stylusProcess.getErrorStream()));
			while ((line = errorReader.readLine()) != null) {
				processErrors += line + "\n";
			}
			if (!processErrors.isEmpty()) {
				Logger.error("%s", processErrors);
				throw new RuntimeException("Stylus compilation error");
			}
			minifyReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (stylusProcess != null) {
				stylusProcess.destroy();
			}
		}
		return compiled;
	}

}
