package tn.megapc.listeners;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.testng.ITestListener;
import org.testng.ITestResult;
import tn.megapc.base.BaseTest;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Livrable 9 : capture d'ecran (PNG) + trace de l'exception (TXT)
 * pour chaque test en echec.
 * Sorties : target/screenshots/ et target/errors/
 */
public class ScreenshotListener implements ITestListener {

    @Override
    public void onTestFailure(ITestResult result) {
        String name = result.getName() + "_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        try {
            Files.createDirectories(Path.of("target/screenshots"));
            Files.createDirectories(Path.of("target/errors"));

            Object instance = result.getInstance();
            if (instance instanceof BaseTest bt && bt.getDriver() != null) {
                File src = ((TakesScreenshot) bt.getDriver()).getScreenshotAs(OutputType.FILE);
                Files.copy(src.toPath(), Path.of("target/screenshots/" + name + ".png"),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            StringWriter sw = new StringWriter();
            if (result.getThrowable() != null) {
                result.getThrowable().printStackTrace(new PrintWriter(sw));
            }
            Files.writeString(Path.of("target/errors/" + name + ".txt"), sw.toString());
        } catch (Exception e) {
            System.err.println("Impossible d'enregistrer la capture / trace : " + e.getMessage());
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        String reason = result.getThrowable() != null ? result.getThrowable().getMessage() : "";
        System.out.println("[SKIPPED] " + result.getName() + " -> " + reason);
    }
}
