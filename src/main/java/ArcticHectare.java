import com.github.romankh3.image.comparison.ImageComparison;
import com.github.romankh3.image.comparison.ImageComparisonUtil;
import com.github.romankh3.image.comparison.model.ImageComparisonResult;
import com.github.romankh3.image.comparison.model.ImageComparisonState;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Slf4j
public class ArcticHectare {

    public static void main(String[] args) {
        try {
            if (Arrays.asList(args).contains("local")) {
                new ArcticHectare(false).operate();
            } else {
                new ArcticHectare(true).operate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    private static final Path REFERENCES_FOLDER = Paths.get("references");

    private final FirefoxDriver driver;
    private final Actions actions;
    private final Wait<FirefoxDriver> wait;

    private final Telegram telegram = new Telegram();
    private final Config.Gosuslugi gosuslugi = Config.get().gosuslugi;
    private final Config.Coordinates coordinates = Config.get().coordinates;
    private String lastOperation;

    public ArcticHectare(boolean headless) {
        FirefoxOptions options = new FirefoxOptions();
        if (headless) {
            options.addArguments("--headless", "--disable-gpu", "--disable-extensions", "--width=1200", "--height=1200");
        } else {
            options.addArguments("--disable-gpu", "--disable-extensions", "--width=1200", "--height=1200");
        }
        this.driver = new FirefoxDriver(options);
        Runtime.getRuntime().addShutdownHook(new Thread(driver::quit));
        this.actions = new Actions(driver);
        this.wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(60))
                .pollingEvery(Duration.ofMillis(500));
    }

    public void operate() throws IOException {
        try {
            openMainPage();
            if (loginGosuslugi()) {
                return;
            }
            openMap();
            navigateAndZoom();
            File file = takeScreenshot();
            compareWithReferences(file);
        } catch (Exception e) {
            warnNotify();
        } finally {
            driver.close();
            driver.quit();
        }
    }

    private void warnNotify() throws IOException {
        telegram.debug(lastOperation);
        telegram.debug(driver.getScreenshotAs(OutputType.FILE));
    }

    private void compareWithReferences(File file) throws IOException {
        BufferedImage actualImage = ImageComparisonUtil.readImageFromResources(file.getAbsolutePath());
        boolean matched = Files.list(REFERENCES_FOLDER)
                .filter(Files::isRegularFile)
                .filter(f -> f.toString().endsWith(".png"))
                .anyMatch(f -> {
                    BufferedImage expectedImage = ImageComparisonUtil.readImageFromResources(f.toFile().getAbsolutePath());
                    ImageComparisonResult comparison = new ImageComparison(expectedImage, actualImage).compareImages();
                    return comparison.getImageComparisonState() == ImageComparisonState.MATCH;
                });

        if (!matched) {
            telegram.notify(file);
            file.renameTo(REFERENCES_FOLDER.resolve(file.getName()).toFile());
        }
    }

    private File takeScreenshot() throws IOException {
        log("Taking screenshot");
        File file = driver.getScreenshotAs(OutputType.FILE);

        log("Wrap up");

        telegram.debug(file);
        if (!Files.exists(REFERENCES_FOLDER)) {
            REFERENCES_FOLDER.toFile().mkdirs();
        } else if (!Files.isDirectory(REFERENCES_FOLDER)) {
            REFERENCES_FOLDER.toFile().delete();
            REFERENCES_FOLDER.toFile().mkdirs();
        }
        return file;
    }

    private void navigateAndZoom() {
        log("Enter coordinates");
        waitAndClick(By.className("coordinate-finder-control__coordinate-finder-control"));

        wait(By.className("coordinate-finder-control__coords"));
        List<WebElement> coords = driver.findElements(By.className("coordinate-finder-control__coords"));

        log("...latitude");
        coords.get(0).click();
        coords.get(0).findElement(By.tagName("input")).sendKeys(coordinates.latitude);
        log("...longtitude");
        coords.get(1).click();
        coords.get(1).findElement(By.tagName("input")).sendKeys(coordinates.longtitude);

        sleep(1);
        log("Close coordinates window");
        waitAndClick(By.className("coordinate-finder-control__confirm"));
        sleep(5);
        waitAndClick(By.className("shared__btn-close"));
        sleep(1);
        log("Zoom 15 times");
        WebElement element = driver.findElement(By.className("zoom-control__zoom-in"));
        IntStream.range(0, 15).forEach(i -> {
            sleep(1);
            element.click();
        });

        log("Sleeping a minute");
        sleep(60);

        log("Click on element");
        actions.moveToElement(driver.findElement(By.className("ol-layer")))
                .click()
                .perform();
        sleep(10);

        driver.findElement(By.className("click-info-popup__results-body"))
                .findElements(By.className("click-info-popup__item-header"))
                .stream().filter(el -> el.getText().endsWith(Config.get().area.id))
                .findFirst()
                .ifPresent(el -> {
                    actions.moveToElement(el).click().perform();
                });
        sleep(10);
    }

    private void openMap() {
        log("Open arctic map");
        driver.navigate().to("https://xn--80aaggvgieoeoa2bo7l.xn--p1ai/default/arctic-map");
    }

    private boolean loginGosuslugi() {
        log("Enter credentials");
        waitAndClick(By.id("login")).sendKeys(gosuslugi.username);
        waitAndClick(By.id("password")).sendKeys(gosuslugi.password);
        log("Click login");
        waitAndClick(By.className("plain-button"));

        if (!handleGosuslugiSuspection()) {
            driver.close();
            driver.quit();
            return true;
        }

        wait(By.className("cabinet__icon-myprofile-svg"));
        return false;
    }

    private void openMainPage() {
        log("Open main page");
        driver.navigate().to("https://xn--80aaggvgieoeoa2bo7l.xn--p1ai/default/login");
    }

    private WebElement waitAndClick(By locator) {
        wait(locator);
        return driver.findElements(locator).stream()
                .peek(WebElement::click)
                .findFirst()
                .orElse(null);
    }

    private void wait(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    private boolean handleGosuslugiSuspection() {
        log("Handle gosuslugi suspection");
        waitPageLoaded();
        try {
            String question = driver.findElement(By.className("anomaly__plain-text")).getText();
            WebElement answer = driver.findElement(By.className("input__field"));
            Optional<Config.Question> qanda = Config.get().gosuslugi.quest.stream().filter(q -> question.equals(q.question)).findFirst();
            if (qanda.isPresent()) {
                answer.sendKeys(qanda.get().answer);
                File file = driver.getScreenshotAs(OutputType.FILE);

                waitAndClick(By.className("anomaly__button"));
                try {
                    wait(By.className("cabinet__icon-myprofile-svg"));
                    return true;
                } catch (Exception e) {
                    telegram.debug(file);
                    return false;
                }
            } else {
                File file = driver.getScreenshotAs(OutputType.FILE);
                telegram.debug(file);
                return false;
            }
        } catch (NoSuchElementException e) {
            log("Seems we are signed in successfully");
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void waitPageLoaded() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState")));
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    private void log(String message) {
        lastOperation = message;
        log.info(lastOperation);
    }
}
