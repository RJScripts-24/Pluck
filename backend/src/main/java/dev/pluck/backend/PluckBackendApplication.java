package dev.pluck.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@SpringBootApplication
public class PluckBackendApplication {

	public static void main(String[] args) {
		loadDotenv();
		SpringApplication.run(PluckBackendApplication.class, args);
	}

	/**
	 * Automatically loads variables from .env file into System properties
	 * if not already set in the operating system environment.
	 */
	private static void loadDotenv() {
		File[] candidates = new File[] {
				new File(".env"),
				new File("../.env"),
				new File("backend/.env")
		};

		for (File f : candidates) {
			if (f.exists() && f.isFile()) {
				try {
					List<String> lines = Files.readAllLines(f.toPath());
					for (String line : lines) {
						String trimmed = line.trim();
						if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
						int eqIdx = trimmed.indexOf('=');
						if (eqIdx > 0) {
							String key = trimmed.substring(0, eqIdx).trim();
							String val = trimmed.substring(eqIdx + 1).trim();
							if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
								val = val.substring(1, val.length() - 1);
							}
							if (System.getProperty(key) == null && System.getenv(key) == null) {
								System.setProperty(key, val);
							}
						}
					}
					break;
				} catch (Exception e) {
					// Fallback to application.properties defaults
				}
			}
		}
	}
}
