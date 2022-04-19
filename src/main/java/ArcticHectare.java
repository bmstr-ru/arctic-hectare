import com.github.romankh3.image.comparison.ImageComparison;
import com.github.romankh3.image.comparison.ImageComparisonUtil;
import com.github.romankh3.image.comparison.model.ImageComparisonResult;
import com.github.romankh3.image.comparison.model.ImageComparisonState;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Slf4j
public class ArcticHectare {

    public static void main(String[] args) throws Exception {
        while (true) {
            try {
                new ArcticHectare().operate();
            } catch (Exception e) {
                e.printStackTrace();
            }
            Thread.sleep(60_000 * 60);
        }
    }

    private static final String SCREENSHOT_FILENAME = "screenshot.png";
    private static final String REFERENCE_FILENAME = "reference.png";
    private static final String EXCEPTION_FILENAME = "exception.png";

    private final ChromeDriver driver;
    private final Actions actions;
    private final Wait<ChromeDriver> wait;

    private final Telegram telegram = new Telegram();
    private final Config.Gosuslugi gosuslugi = Config.get().gosuslugi;
    private final Config.Coordinates coordinates = Config.get().coordinates;

    private final File referenceFile = new File(REFERENCE_FILENAME);
    private final File screenshotFile = new File(SCREENSHOT_FILENAME);
    private final File exceptionFile = new File(EXCEPTION_FILENAME);

    public ArcticHectare() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--no-sandbox", "--disable-extensions", "--window-size=1920,1200");
        this.driver = new ChromeDriver(options);
        Runtime.getRuntime().addShutdownHook(new Thread(driver::quit));
        this.actions = new Actions(driver);
        this.wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(60))
                .pollingEvery(Duration.ofMillis(500));
    }

    public void operate() throws IOException {
//        telegram.debug("Job started");

        try {
            log.info("Open main page");
            driver.navigate().to("https://xn--80aaggvgieoeoa2bo7l.xn--p1ai/default/login");
            log.info("Maximize window");
            driver.manage().window().maximize();
            log.info("Enter credentials");
            waitAndClick(By.id("login")).sendKeys(gosuslugi.username);
            waitAndClick(By.id("password")).sendKeys(gosuslugi.password);
            log.info("Click login");
            waitAndClick(By.id("loginByPwdButton"));

            if (!handleGosuslugiSuspection()) {
                driver.close();
                driver.quit();
                return;
            }

            wait(By.className("cabinet__icon-myprofile-svg"));

            log.info("Open arctic map");
            driver.navigate().to("https://xn--80aaggvgieoeoa2bo7l.xn--p1ai/default/arctic-map");

            log.info("Enter coordinates");
            waitAndClick(By.className("coordinate-finder-control__coordinate-finder-control"));

            wait(By.className("coordinate-finder-control__coords"));
            List<WebElement> coords = driver.findElements(By.className("coordinate-finder-control__coords"));

            log.info("...latitude");
            coords.get(0).click();
            coords.get(0).findElement(By.tagName("input")).sendKeys(coordinates.latitude);
            log.info("...longtitude");
            coords.get(1).click();
            coords.get(1).findElement(By.tagName("input")).sendKeys(coordinates.longtitude);

            sleep(1);
            log.info("Close coordinates window");
            waitAndClick(By.className("coordinate-finder-control__confirm"));
            sleep(5);
            waitAndClick(By.className("shared__btn-close"));
            sleep(1);
            log.info("Zoom 15 times");
            WebElement element = driver.findElement(By.className("zoom-control__zoom-in"));
            IntStream.range(0, 15).forEach(i -> {
                sleep(1);
                element.click();
            });

            log.info("Sleeping a minute");
            sleep(60);

            log.info("Click on element");
            actions.moveToElement(driver.findElement(By.className("ol-layer")))
                    .click()
                    .perform();
            sleep(10);

            log.info("Taking screenshot");
            File file = driver.getScreenshotAs(OutputType.FILE);

            log.info("Wrap up");
            driver.close();
            driver.quit();

            telegram.debug(file);
            if (!referenceFile.exists()) {
                file.renameTo(referenceFile);
            } else {
                file.renameTo(screenshotFile);
                BufferedImage expectedImage = ImageComparisonUtil.readImageFromResources(REFERENCE_FILENAME);
                BufferedImage actualImage = ImageComparisonUtil.readImageFromResources(SCREENSHOT_FILENAME);
                ImageComparisonResult comparisonResult = new ImageComparison(expectedImage, actualImage).compareImages();

                if (comparisonResult.getImageComparisonState() != ImageComparisonState.MATCH) {
                    telegram.notify(screenshotFile);
                }
            }
//            telegram.debug("Job finished");
        } catch (Exception e) {
            log.warn("Error", e);
            telegram.debug("Error: " + e.getMessage());
            File file = driver.getScreenshotAs(OutputType.FILE);
            if (file.renameTo(exceptionFile)) {
                telegram.debug(exceptionFile);
            }
        }
    }

    private WebElement waitAndClick(By locator) {
        wait(locator);
        WebElement element = driver.findElement(locator);
        element.click();
        return element;
    }

    private void wait(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    private boolean handleGosuslugiSuspection() {
        log.info("Handle gosuslugi suspection");
        waitPageLoaded();
        try {
            String question = driver.findElement(By.id("question")).getText();
            WebElement answer = driver.findElement(By.id("answer"));
            Optional<Config.Question> qanda = Config.get().gosuslugi.quest.stream().filter(q -> question.equals(q.question)).findFirst();
            if (qanda.isPresent()) {
                answer.sendKeys(qanda.get().answer);
                File file = driver.getScreenshotAs(OutputType.FILE);

                waitAndClick(By.id("button-reqinfo-submit"));
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
            log.info("Seems we are signed in successfully");
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
}
