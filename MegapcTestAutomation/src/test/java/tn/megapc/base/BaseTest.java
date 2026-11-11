package tn.megapc.base;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.ITestResult;
import org.testng.SkipException;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classe de base : demarrage / arret du navigateur + methodes utilitaires.
 *
 * megapc.tn est une application Next.js (rendu cote client) :
 *  - les produits sont des liens  a[href*='/shop/product/']
 *  - la recherche se fait via     /shop/search/<terme>
 *  - les categories via           /shop/<CATEGORIE>/<SOUS-CATEGORIE>
 * Il n'y a pas de classes WooCommerce / PrestaShop.
 */
public abstract class BaseTest implements IHookable {

    /** Surchargeable : -DbaseUrl=http://localhost:8080 (utile pour tester les tests sur une page maquette). */
    protected static final String BASE_URL = System.getProperty("baseUrl", "https://megapc.tn");

    /**
     * MODE DEMONSTRATION : delai (ms) entre les actions pour que le jury puisse suivre.
     * Defaut 1500 ms. Plus lent : -Ddelay=2500 ; execution rapide : -Ddelay=0
     */
    protected static final long DELAY = Long.getLong("delay", 1500L);
    protected static final String RED = "#e53935";
    protected static final String GREEN = "#2e7d32";

    /** Les tests « ignores volontairement » ne s'executent que avec -Drun.ignored=true. */
    protected static final boolean RUN_IGNORED = Boolean.getBoolean("run.ignored");

    protected String currentTest = "";

    protected WebDriver driver;
    protected WebDriverWait wait;

    public WebDriver getDriver() {
        return driver;
    }

    // ------------------------------------------------------------------
    //  Cycle de vie
    // ------------------------------------------------------------------

    // NB : pas d'annotations @BeforeClass/@BeforeMethod/@AfterMethod/@AfterClass.
    // Le cycle de vie passe par IHookable.run() : IntelliJ n'affiche ainsi que les 20 vrais tests.

    /** Enveloppe chaque test : (re)demarre Chrome si besoin, ouvre l'accueil, lance le test, fait la capture finale. */
    @Override
    public void run(IHookCallBack callBack, ITestResult testResult) {
        ensureDriver();                           // une exception ici = echec du test
        goHome(testResult.getMethod().getConstructorOrMethod().getMethod());
        callBack.runTestMethod(testResult);       // execute le test (le resultat est stocke dans testResult)
        afterEach(testResult);
    }

    /** Demarre Chrome, ou le redemarre si la session a ete perdue. */
    private void ensureDriver() {
        if (driver != null) {
            try {
                driver.getWindowHandle();          // leve une exception si la session est morte
                return;
            } catch (RuntimeException dead) {
                try {
                    driver.quit();
                } catch (RuntimeException ignored) {
                    // deja ferme
                }
                driver = null;
            }
        }
        setUp();
    }

    /** Demarre Chrome. */
    public void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");
        options.addArguments("--remote-allow-origins=*");
        // Mode sans fenetre : mvn test -Dheadless=true
        if (Boolean.getBoolean("headless")) {
            options.addArguments("--headless=new", "--window-size=1920,1080");
        }
        driver = new ChromeDriver(options);   // Selenium Manager gere le driver
        final WebDriver started = driver;
        // Fermeture du navigateur a la fin de la JVM (remplace @AfterClass)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                started.quit();
            } catch (RuntimeException ignored) {
                // deja ferme
            }
        }));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    /** Avant chaque test : demarre Chrome si besoin puis ouvre l'accueil. */
    public void goHome(Method method) {
        currentTest = method.getName();
        System.out.println("\n========== TEST : " + currentTest + " ==========");
        open("/");
    }

    /** Apres chaque test : capture des tests reussis + pause pour le jury. */
    public void afterEach(ITestResult result) {
        boolean success = result.getThrowable() == null;
        // Capture finale de chaque test reussi (preuve pour le rapport) + pause pour le jury
        try {
            if (success && driver != null) {
                Files.createDirectories(Path.of("target/screenshots/passed"));
                File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Files.copy(src.toPath(), Path.of("target/screenshots/passed/" + result.getName() + ".png"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // la capture est un bonus : ne jamais faire echouer un test
        }
        String status = success ? "PASSED"
                : result.getThrowable() instanceof SkipException ? "SKIPPED" : "FAILED";
        System.out.println("---------- " + currentTest + " -> " + status);
        pause(2.0);
    }

    /** Ferme Chrome (appele une seule fois, a la fin de la suite). */
    public void tearDown() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    // ------------------------------------------------------------------
    //  Navigation / attentes
    // ------------------------------------------------------------------

    protected void open(String pathOrUrl) {
        driver.get(pathOrUrl.startsWith("http") ? pathOrUrl : BASE_URL + pathOrUrl);
        waitForPage();
        pause(1.0);
    }

    // ------------------------------------------------------------------
    //  Mode demonstration : pauses, surlignage, saisie lente, bandeau d'etape
    // ------------------------------------------------------------------

    protected void pause(double factor) {
        if (DELAY > 0) {
            sleep((long) (DELAY * factor));
        }
    }

    /** Affiche l'etape en console et dans un bandeau en haut de la page (visible par le jury). */
    protected void step(String message) {
        System.out.println("  > " + message);
        if (DELAY <= 0 || driver == null) {
            return;
        }
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "var id='mega-test-banner'; var b=document.getElementById(id);"
                    + "if(!b){b=document.createElement('div'); b.id=id;"
                    + "b.style.cssText='position:fixed;top:0;left:0;right:0;z-index:2147483647;"
                    + "background:#1F3864;color:#fff;font:bold 15px Arial;padding:8px 14px;"
                    + "pointer-events:none;opacity:.93';"
                    + "document.documentElement.appendChild(b);}"
                    + "b.textContent=arguments[0];",
                    currentTest + "  |  " + message);
        } catch (Exception ignored) {
            // page en cours de navigation : le bandeau est facultatif
        }
        pause(0.6);
    }

    protected void highlight(WebElement element) {
        highlight(element, RED);
    }

    /** Entoure l'element d'un cadre colore (rouge = action, vert = verification). */
    protected void highlight(WebElement element, String color) {
        if (DELAY <= 0) {
            return;
        }
        JavascriptExecutor js = (JavascriptExecutor) driver;
        try {
            js.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
            Object old = js.executeScript(
                    "var o=arguments[0].style.cssText;"
                    + "arguments[0].style.cssText+=';outline:4px solid ' + arguments[1] + ' !important;"
                    + "outline-offset:2px;background-color:rgba(255,235,59,.45) !important;'; return o;",
                    element, color);
            pause(0.7);
            js.executeScript("arguments[0].style.cssText=arguments[1];", element, old);
        } catch (Exception ignored) {
            // element devenu obsolete : le surlignage est facultatif
        }
    }

    /** Saisie caractere par caractere (visible) dans un champ. */
    protected void type(WebElement element, String text) {
        highlight(element);
        try {
            element.clear();
        } catch (Exception ignored) {
            // certains champs ne supportent pas clear()
        }
        for (char c : text.toCharArray()) {
            element.sendKeys(String.valueOf(c));
            if (DELAY > 0) {
                sleep(70);
            }
        }
        pause(0.5);
    }

    /** Attend readyState=complete puis la disparition des "Loading..." du site. */
    protected void waitForPage() {
        wait.until(d -> "complete".equals(
                ((JavascriptExecutor) d).executeScript("return document.readyState")));
        try {
            new WebDriverWait(driver, Duration.ofSeconds(15)).until(d ->
                    d.findElements(By.xpath("//*[normalize-space(text())='Loading...']")).isEmpty());
        } catch (TimeoutException ignored) {
            // on continue : certains blocs peuvent rester en chargement
        }
    }

    /**
     * Test IGNORE VOLONTAIREMENT (statut SKIPPED, raison affichee dans le rapport).
     * Pour l'executer quand meme : mvn clean test -Drun.ignored=true
     */
    protected void ignoreByDesign(String reason) {
        if (!RUN_IGNORED) {
            throw new SkipException("IGNORE VOLONTAIREMENT : " + reason + " (executer avec -Drun.ignored=true)");
        }
    }

    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected boolean waitUntil(Supplier<Boolean> condition, int seconds) {
        long end = System.currentTimeMillis() + seconds * 1000L;
        do {
            try {
                if (Boolean.TRUE.equals(condition.get())) {
                    return true;
                }
            } catch (Exception ignored) {
                // element pas encore pret / obsolete : on re-essaie
            }
            sleep(250);
        } while (System.currentTimeMillis() < end);
        return false;
    }

    // ------------------------------------------------------------------
    //  Elements
    // ------------------------------------------------------------------

    protected Optional<WebElement> firstDisplayed(By by) {
        for (WebElement e : driver.findElements(by)) {
            try {
                if (e.isDisplayed()) {
                    return Optional.of(e);
                }
            } catch (StaleElementReferenceException ignored) {
                // suivant
            }
        }
        return Optional.empty();
    }

    protected Optional<WebElement> waitForDisplayed(By by, int seconds) {
        long end = System.currentTimeMillis() + seconds * 1000L;
        do {
            Optional<WebElement> e = firstDisplayed(by);
            if (e.isPresent()) {
                return e;
            }
            sleep(250);
        } while (System.currentTimeMillis() < end);
        return Optional.empty();
    }

    protected void jsClick(WebElement element) {
        highlight(element);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
        js.executeScript("arguments[0].click();", element);
        pause(1.0);
    }

    protected String bodyText() {
        return driver.findElement(By.tagName("body")).getText().toLowerCase();
    }

    /**
     * Lien ou bouton dont le texte (ou aria-label / title) contient l'un des mots
     * (insensible a la casse). Eviter les accents dans les mots recherches.
     */
    protected By clickableWithText(String... words) {
        return textXpath("self::a or self::button or @role='button'", words);
    }

    /** Comme clickableWithText mais uniquement les boutons (evite de cliquer sur le lien « Panier » de l'en-tete). */
    protected By buttonWithText(String... words) {
        return textXpath("self::button or @role='button' or (self::input and (@type='button' or @type='submit'))", words);
    }

    private By textXpath(String tags, String... words) {
        String up = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String low = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder("//*[(" + tags + ") and (");
        for (int i = 0; i < words.length; i++) {
            String w = words[i].toLowerCase();
            if (i > 0) {
                sb.append(" or ");
            }
            sb.append("contains(translate(normalize-space(.),'").append(up).append("','").append(low)
              .append("'),'").append(w).append("')")
              .append(" or contains(translate(@aria-label,'").append(up).append("','").append(low)
              .append("'),'").append(w).append("')")
              .append(" or contains(translate(@title,'").append(up).append("','").append(low)
              .append("'),'").append(w).append("')")
              .append(" or contains(translate(@value,'").append(up).append("','").append(low)
              .append("'),'").append(w).append("')");
        }
        sb.append(")]");
        return By.xpath(sb.toString());
    }

    /**
     * Ecrit dans target/dom-dump/ la liste des liens / boutons / champs de la page courante
     * (href, texte, aria-label, classe). A joindre au rapport quand un test est SKIPPED :
     * permet d'adapter les locators sans deviner.
     */
    protected void dumpClickables(String tag) {
        try {
            Object dump = ((JavascriptExecutor) driver).executeScript(
                    "return Array.from(document.querySelectorAll('a,button,[role=button],input,select,textarea'))"
                    + ".slice(0,500).map(function(e){return e.tagName+' | href='+(e.getAttribute('href')||'')"
                    + "+' | text='+(e.innerText||e.value||'').trim().slice(0,40).replace(/\\s+/g,' ')"
                    + "+' | aria='+(e.getAttribute('aria-label')||'')+' | title='+(e.getAttribute('title')||'')"
                    + "+' | type='+(e.getAttribute('type')||'')+' | name='+(e.getAttribute('name')||'')"
                    + "+' | class='+(e.getAttribute('class')||'').slice(0,70);}).join('\\n');");
            Files.createDirectories(Path.of("target/dom-dump"));
            Path out = Path.of("target/dom-dump/" + currentTest + "_" + tag + ".txt");
            Files.writeString(out, driver.getCurrentUrl() + "\n" + dump);
            System.out.println("  [DOM] export des elements cliquables : " + out);
        } catch (Exception ignored) {
            // l'export est un bonus
        }
    }

    // ------------------------------------------------------------------
    //  Produits / prix
    // ------------------------------------------------------------------

    protected List<WebElement> productLinks() {
        return driver.findElements(By.cssSelector("a[href*='/shop/product/']"));
    }

    protected void waitForProducts() {
        boolean ok = waitUntil(() -> !productLinks().isEmpty(), 25);
        if (!ok) {
            throw new TimeoutException("Aucun produit affiche apres 25 s : " + driver.getCurrentUrl());
        }
    }

    /** Extrait les prix ("26 999 DT" -> 26999) des cartes produits, ignore "0 DT". */
    protected List<Long> extractPrices() {
        List<Long> prices = new ArrayList<>();
        Pattern p = Pattern.compile("(\\d[\\d \\u00A0.]*)(?:,\\d+)?\\s*DT");
        for (WebElement a : productLinks()) {
            Matcher m = p.matcher(a.getText());
            if (m.find()) {
                String digits = m.group(1).replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    long v = Long.parseLong(digits);
                    if (v > 0) {
                        prices.add(v);
                    }
                }
            }
        }
        return prices;
    }

    // ------------------------------------------------------------------
    //  Defauts d'affichage des cartes produit (prix 0 DT, image « AFFICHE EN COURS »)
    // ------------------------------------------------------------------

    /** Pages scannees pour les defauts d'affichage : accueil + deux listings de produits. */
    protected static final String[] CARD_PAGES = {
            "/", "/shop/ECRANS/ECRANS%20GAMING", "/shop/PC%20PORTABLE/PC%20PORTABLE%20GAMER"};

    /** Fait defiler toute la page (charge les images / sections « lazy »), puis revient en haut. */
    protected void scrollWholePage() {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long y = 0;
        for (int i = 0; i < 60; i++) {
            long height = ((Number) js.executeScript("return document.body.scrollHeight;")).longValue();
            if (y >= height) {
                break;
            }
            y += 700;
            js.executeScript("window.scrollTo(0, arguments[0]);", y);
            sleep(250);
        }
        sleep(500);
        js.executeScript("window.scrollTo(0, 0);");
    }

    /** Cartes produit visibles, une par lien (les doublons de carrousel sont ignores). */
    protected List<WebElement> visibleProductCards() {
        List<WebElement> cards = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (WebElement a : productLinks()) {
            try {
                String href = a.getAttribute("href");
                if (a.isDisplayed() && href != null && !seen.contains(href)) {
                    seen.add(href);
                    cards.add(a);
                }
            } catch (StaleElementReferenceException ignored) {
                // carte remplacee pendant le defilement : suivante
            }
        }
        return cards;
    }

    /** Nom lisible d'une carte (premiere ligne qui n'est ni « New » ni un prix). */
    protected String cardLabel(WebElement card) {
        try {
            for (String line : card.getText().split("\\R")) {
                String t = line.trim();
                if (!t.isEmpty() && !t.equalsIgnoreCase("new") && !CardDefects.hasZeroPrice(t)
                        && !t.matches("(?i).*\\d\\s*DT.*") && !t.equalsIgnoreCase("Prix sur demande")) {
                    return t;
                }
            }
            return card.getAttribute("href");
        } catch (StaleElementReferenceException e) {
            return "(carte obsolete)";
        }
    }

    /**
     * Raison pour laquelle l'image d'une carte est un faux visuel (« AFFICHE EN COURS », image cassee...),
     * ou null si l'image est correcte. {@code sharedImages} : cle d'image -> nombre de produits qui l'utilisent.
     */
    @SuppressWarnings("unchecked")
    protected String placeholderReason(WebElement card, java.util.Map<String, Integer> sharedImages) {
        List<java.util.Map<String, Object>> imgs = (List<java.util.Map<String, Object>>) ((JavascriptExecutor) driver)
                .executeScript(
                        "var out=[]; arguments[0].querySelectorAll('img').forEach(function(i){"
                        + "out.push({src:i.currentSrc||i.src||'',"
                        + "raw:(i.getAttribute('src')||'')+' '+(i.getAttribute('srcset')||'')+' '+(i.getAttribute('data-src')||''),"
                        + "alt:i.alt||'', title:i.title||'',"
                        + "broken:(i.complete && i.naturalWidth===0 && (i.currentSrc||i.src)!=='')});});"
                        + "return out;", card);
        if (imgs.isEmpty()) {
            return "aucune image dans la carte";
        }
        for (java.util.Map<String, Object> img : imgs) {
            String descriptor = img.get("src") + " " + img.get("raw") + " " + img.get("alt") + " " + img.get("title");
            if (CardDefects.looksLikePlaceholder(descriptor)) {
                return "image de remplacement (« AFFICHE EN COURS ») : " + img.get("src");
            }
            if (Boolean.TRUE.equals(img.get("broken"))) {
                return "image cassee (ne se charge pas) : " + img.get("src");
            }
            Integer n = sharedImages.get(CardDefects.imageKey(String.valueOf(img.get("src"))));
            if (n != null && n >= 2) {
                return "meme image que " + (n - 1) + " autre(s) produit(s) : " + img.get("src");
            }
        }
        return null;
    }

    /** Compte, pour chaque image, le nombre de cartes differentes qui l'utilisent. */
    protected java.util.Map<String, Integer> countSharedImages(List<WebElement> cards) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (WebElement card : cards) {
            try {
                java.util.Set<String> keys = new java.util.HashSet<>();
                for (WebElement img : card.findElements(By.tagName("img"))) {
                    String key = CardDefects.imageKey(img.getAttribute("currentSrc") != null
                            && !img.getAttribute("currentSrc").isEmpty()
                            ? img.getAttribute("currentSrc") : img.getAttribute("src"));
                    if (!key.isEmpty()) {
                        keys.add(key);
                    }
                }
                keys.forEach(k -> counts.merge(k, 1, Integer::sum));
            } catch (StaleElementReferenceException ignored) {
                // suivante
            }
        }
        return counts;
    }

    /**
     * Encadre l'element defectueux en rouge de facon PERMANENTE (contrairement a highlight()),
     * pour qu'il soit visible sur la capture d'ecran prise automatiquement a l'echec du test.
     */
    protected void markDefect(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].style.setProperty('outline','5px solid #e53935','important');"
                    + "arguments[0].style.setProperty('outline-offset','3px','important');"
                    + "arguments[0].style.setProperty('background-color','rgba(255,235,59,.45)','important');",
                    element);
        } catch (Exception ignored) {
            // element obsolete : le marquage est facultatif
        }
    }

    /** Centre la carte dans la fenetre (pour la capture d'echec). */
    protected void scrollToCenter(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
            sleep(400);
        } catch (Exception ignored) {
            // facultatif
        }
    }

    // ------------------------------------------------------------------
    //  Recherche
    // ------------------------------------------------------------------

    /** Utilise la barre de recherche si elle est trouvee, sinon l'URL /shop/search/<terme>. */
    protected void searchFor(String term) {
        step("Recherche du terme : " + term);
        By input = By.cssSelector("input[type='search'], input[placeholder*='cherch' i], "
                + "input[placeholder*='search' i], input[name='search'], input[name='q'], input[name='s']");
        Optional<WebElement> box = firstDisplayed(input);
        if (box.isPresent()) {
            try {
                type(box.get(), term);
                box.get().sendKeys(org.openqa.selenium.Keys.ENTER);
                if (waitUntil(() -> driver.getCurrentUrl().toLowerCase().contains("search"), 10)) {
                    waitForPage();
                    return;
                }
            } catch (Exception ignored) {
                // on retombe sur l'URL de recherche
            }
        }
        String encoded = URLEncoder.encode(term, StandardCharsets.UTF_8).replace("+", "%20");
        open("/shop/search/" + encoded);
    }

    // ------------------------------------------------------------------
    //  Panier
    // ------------------------------------------------------------------

    /** Vrai si l'element est dans l'en-tete / la navigation du site (icone panier, menu...). */
    protected boolean inHeader(WebElement e) {
        try {
            return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript(
                    "return !!arguments[0].closest('header, nav, [role=navigation], [role=dialog], [class*=drawer i], [class*=sidebar i]');", e));
        } catch (Exception ex) {
            return false;
        }
    }

    /** Premier element visible du locator qui n'est PAS dans l'en-tete ni dans un tiroir (evite l'icone « Panier »). */
    protected Optional<WebElement> firstDisplayedOutsideHeader(By by) {
        for (WebElement e : driver.findElements(by)) {
            try {
                if (e.isDisplayed() && !inHeader(e)) {
                    return Optional.of(e);
                }
            } catch (StaleElementReferenceException ignored) {
                // suivant
            }
        }
        return Optional.empty();
    }

    /** Vrai si le texte de la page indique un panier vide (tiroir « Votre panier est vide »). */
    protected boolean cartLooksEmpty() {
        String t = bodyText();
        return t.contains("panier est vide") || t.contains("cart is empty");
    }

    /**
     * Ajoute un produit au panier (essaie les 12 premiers produits de PC PORTABLE GAMER).
     * Ne clique QUE sur le bouton « Ajouter » de la fiche produit (jamais sur l'icone panier de l'en-tete,
     * qui ouvre seulement le tiroir sans rien ajouter).
     */
    protected void addFirstProductToCart() {
        step("Ajout d'un produit au panier");
        open("/shop/PC%20PORTABLE/PC%20PORTABLE%20GAMER");
        waitForProducts();
        List<String> hrefs = new ArrayList<>();
        for (WebElement a : productLinks()) {
            String h = a.getAttribute("href");
            if (h != null && !hrefs.contains(h) && hrefs.size() < 12) {
                hrefs.add(h);
            }
        }
        for (String href : hrefs) {
            open(href);
            By addButton = buttonWithText("ajouter", "add to cart", "add to bag");
            Optional<WebElement> btn = Optional.empty();
            long end = System.currentTimeMillis() + 8000;
            while (btn.isEmpty() && System.currentTimeMillis() < end) {
                btn = firstDisplayedOutsideHeader(addButton);
                if (btn.isEmpty()) {
                    btn = firstDisplayedOutsideHeader(By.cssSelector(
                            "button[class*='add' i][class*='cart' i], button[id*='add' i], [data-testid*='add' i]"));
                }
                if (btn.isEmpty()) {
                    sleep(250);
                }
            }
            if (btn.isEmpty()) {
                continue;   // produit sans bouton (rupture, prix sur demande...) : suivant
            }
            jsClick(btn.get());
            sleep(1500);
            // si le tiroir s'ouvre tout seul et dit « vide », l'ajout n'a pas eu lieu : produit suivant
            if (cartLooksEmpty()) {
                System.out.println("  [panier] ajout sans effet sur " + href + " : produit suivant");
                continue;
            }
            return;
        }
        dumpClickables("fiche_produit");
        throw new SkipException("Aucun bouton 'Ajouter au panier' efficace trouve (voir target/dom-dump/) : adapter BaseTest.addFirstProductToCart().");
    }

    /** Vrai si le tiroir « Panier » (ou la page panier) est affiche. */
    protected boolean cartDrawerOpen() {
        String t = bodyText();
        return t.contains("sous-total") || t.contains("panier est vide") || t.contains("passer commande")
                || driver.getCurrentUrl().toLowerCase().matches(".*(/cart|/panier).*");
    }

    /**
     * Ouvre le panier (tiroir lateral « Panier »). Ne recharge PAS la page (cela pourrait vider le panier).
     * Essaie les candidats un par un (en-tete d'abord) et ne s'arrete que lorsque le tiroir est reellement
     * affiche. Skip si rien n'ouvre le panier.
     */
    protected void openCart() {
        step("Ouverture du panier");
        if (cartDrawerOpen()) {
            return;
        }
        String[] selectors = {
                "header a[href*='cart' i], header a[href*='panier' i], nav a[href*='cart' i], nav a[href*='panier' i]",
                "header [class*='cart' i], header [class*='panier' i], header [class*='basket' i]",
                "header [aria-label*='panier' i], header [aria-label*='cart' i], header [title*='panier' i], header [title*='cart' i]",
                "nav [class*='cart' i], nav [aria-label*='panier' i], nav [aria-label*='cart' i]",
                "a[href*='cart' i], a[href*='panier' i], [aria-label*='panier' i], [aria-label*='cart' i], "
                        + "[title*='panier' i], [data-testid*='cart' i], [class*='cart-icon' i], [class*='cart_icon' i]",
                "header button:has(svg), header a:has(svg), header [role='button']:has(svg)"
        };
        List<WebElement> candidates = new ArrayList<>();
        for (String css : selectors) {
            for (WebElement e : driver.findElements(By.cssSelector(css))) {
                try {
                    String text = e.getText() == null ? "" : e.getText().toLowerCase();
                    if (e.isDisplayed() && !text.contains("ajouter") && !candidates.contains(e)) {
                        candidates.add(e);
                    }
                } catch (StaleElementReferenceException ignored) {
                    // suivant
                }
            }
        }
        candidates.addAll(driver.findElements(clickableWithText("panier", "cart")).stream()
                .filter(e -> { try { return e.isDisplayed() && !candidates.contains(e); } catch (Exception x) { return false; } })
                .toList());

        int tried = 0;
        for (WebElement c : candidates) {
            if (tried++ >= 10) {
                break;
            }
            try {
                jsClick(c);
            } catch (Exception ignored) {
                continue;
            }
            if (waitUntil(this::cartDrawerOpen, 3)) {
                return;
            }
        }
        for (String path : new String[]{"/cart", "/panier", "/shop/cart", "/shop/panier", "/basket", "/checkout"}) {
            open(path);
            if (cartDrawerOpen()) {
                return;
            }
        }
        dumpClickables("entete");
        throw new SkipException("Le panier ne s'ouvre avec aucun element de l'en-tete (voir target/dom-dump/) : adapter BaseTest.openCart().");
    }

    // ---- Contenu du tiroir « Panier » -----------------------------------

    /** JS commun : retrouve la racine du tiroir (ancetre « fixed » qui contient « Sous-total »). */
    private static final String CART_ROOT_JS =
            "function cartRoot(){var a=null;"
            + "var els=document.querySelectorAll('div,span,p,h2,h3,strong,b,dt,dd,label');"
            + "for(var i=0;i<els.length;i++){var e=els[i];"
            + "if(e.children.length===0&&/sous-total/i.test(e.textContent)&&e.getClientRects().length>0){a=e;break;}}"
            + "if(!a)return null;"
            + "var r=a;while(r&&r!==document.body){if(getComputedStyle(r).position==='fixed')return r;r=r.parentElement;}"
            + "return a.closest('[role=dialog],aside,[class*=drawer i],[class*=cart i],[class*=sidebar i]')||document.body;}";

    /** Nombre d'articles distincts dans le tiroir (0 = vide, -1 = tiroir non ouvert). */
    protected int cartItemCount() {
        if (cartLooksEmpty()) {
            return 0;
        }
        Object n = ((JavascriptExecutor) driver).executeScript(CART_ROOT_JS
                + "var r=cartRoot();if(!r)return -1;var h={};"
                + "r.querySelectorAll('a[href*=\"/shop/product/\"]').forEach(function(a){h[a.getAttribute('href')]=1;});"
                + "var n=Object.keys(h).length;if(n===0)n=r.querySelectorAll('img').length;return n;");
        return n == null ? -1 : ((Number) n).intValue();
    }

    /** Texte du sous-total affiche dans le tiroir (ex. « 18 249 DT »), ou "" si introuvable. */
    protected String cartSubtotal() {
        Object t = ((JavascriptExecutor) driver).executeScript(CART_ROOT_JS
                + "var r=cartRoot();if(!r)return '';var m=r.innerText.match(/sous-total[^\\d]*([\\d\\s.,]+\\s*DT)/i);"
                + "return m?m[1].trim():'';");
        return t == null ? "" : t.toString();
    }

    /**
     * Bouton « supprimer » d'un article, DANS le tiroir : libelle / aria-label / classe contenant
     * supprimer, retirer, remove, delete, trash, poubelle ; a defaut, bouton-icone (svg) place sous
     * l'en-tete du tiroir. Le bouton de fermeture (X) est exclu.
     */
    protected Optional<WebElement> cartRemoveControl() {
        Object o = ((JavascriptExecutor) driver).executeScript(CART_ROOT_JS
                + "var r=cartRoot();if(!r)return null;"
                + "var els=Array.from(r.querySelectorAll('button,[role=button],a,span,div,svg'))"
                + ".filter(function(e){return e.getClientRects().length>0;});"
                + "function d(e){return ((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '"
                + "+(e.getAttribute('class')||'').toString()+' '+(e.getAttribute('data-testid')||'')+' '"
                + "+(e.id||'')+' '+(e.innerText||'')).toLowerCase();}"
                + "var hit=els.filter(function(e){var t=d(e);"
                + "return /supprim|retirer|remove|delete|trash|poubelle/.test(t)&&!/close|fermer/.test(t);});"
                + "if(hit.length)return hit[hit.length-1];"
                + "var first=r.querySelector('a[href*=\"/shop/product/\"],img');"
                + "var top=first?first.getBoundingClientRect().top-20:r.getBoundingClientRect().top+90;"
                + "var icons=els.filter(function(e){var b=e.closest('button,[role=button],a');"
                + "return b&&(e.tagName.toLowerCase()==='svg'||b.querySelector('svg'))&&!(b.innerText||'').trim()"
                + "&&b.getBoundingClientRect().top>top&&!/close|fermer/.test(d(b));})"
                + ".map(function(e){return e.closest('button,[role=button],a');});"
                + "return icons.length?icons[0]:null;");
        return o instanceof WebElement ? Optional.of((WebElement) o) : Optional.empty();
    }

    /** Enregistre le HTML du tiroir (target/dom-dump/) pour adapter les locators. */
    protected void dumpCartHtml(String tag) {
        try {
            Object html = ((JavascriptExecutor) driver).executeScript(CART_ROOT_JS
                    + "var r=cartRoot();return r?r.outerHTML.slice(0,60000):'';");
            Files.createDirectories(Path.of("target/dom-dump"));
            Path out = Path.of("target/dom-dump/" + currentTest + "_" + tag + ".html");
            Files.writeString(out, String.valueOf(html));
            System.out.println("  [DOM] HTML du tiroir panier : " + out);
        } catch (Exception ignored) {
            // bonus
        }
    }

    /** Element visible qui n'est pas un bouton « Ajouter au panier » de fiche produit. */
    private Optional<WebElement> firstDisplayedOutsideProductCard(By by) {
        for (WebElement e : driver.findElements(by)) {
            try {
                String text = (e.getText() == null ? "" : e.getText()).toLowerCase();
                if (e.isDisplayed() && !text.contains("ajouter")) {
                    return Optional.of(e);
                }
            } catch (StaleElementReferenceException ignored) {
                // suivant
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    //  Compte (connexion / inscription)
    // ------------------------------------------------------------------

    protected boolean hasPasswordField() {
        return waitForDisplayed(By.cssSelector("input[type='password']"), 5).isPresent();
    }

    /** Ouvre la page / fenetre de connexion. Skip si le site n'en propose pas. */
    protected void openLoginPage() {
        step("Ouverture de la page de connexion");
        Optional<WebElement> link = firstDisplayed(By.cssSelector(
                "a[href*='login' i], a[href*='connexion' i], a[href*='signin' i], a[href*='sign-in' i], "
                + "a[href*='account' i], a[href*='compte' i], a[href*='auth' i], a[href*='user' i]"));
        if (link.isEmpty()) {
            link = firstDisplayed(clickableWithText("connexion", "se connecter", "login", "sign in", "compte", "account"));
        }
        if (link.isEmpty()) {
            // icone « utilisateur » sans texte
            link = firstDisplayed(By.cssSelector(
                    "[class*='user' i], [class*='account' i], [class*='login' i], [class*='profile' i], "
                    + "[aria-label*='compte' i], [aria-label*='account' i], [aria-label*='connex' i], "
                    + "[title*='compte' i], [title*='connex' i], [data-testid*='user' i], [data-testid*='login' i]"));
        }
        if (link.isPresent()) {
            jsClick(link.get());
            waitForPage();
            if (hasPasswordField()) {
                return;
            }
            // menu deroulant : l'icone ouvre une liste contenant « Se connecter »
            Optional<WebElement> inner = firstDisplayed(
                    clickableWithText("se connecter", "connexion", "login", "sign in", "identifier"));
            if (inner.isPresent()) {
                jsClick(inner.get());
                waitForPage();
                if (hasPasswordField()) {
                    return;
                }
            }
        }
        for (String path : new String[]{"/login", "/connexion", "/account", "/auth/login", "/signin",
                "/sign-in", "/shop/login", "/account/login", "/user/login", "/se-connecter", "/mon-compte", "/auth"}) {
            open(path);
            if (hasPasswordField()) {
                return;
            }
        }
        open("/");
        dumpClickables("entete");
        throw new SkipException("Formulaire de connexion introuvable (voir target/dom-dump/) : adapter BaseTest.openLoginPage().");
    }

    protected void clickSubmit() {
        Optional<WebElement> btn = firstDisplayed(By.cssSelector("button[type='submit'], input[type='submit']"));
        if (btn.isEmpty()) {
            btn = firstDisplayed(clickableWithText("se connecter", "connexion", "login", "inscri", "register", "valider"));
        }
        if (btn.isEmpty()) {
            throw new SkipException("Bouton de validation du formulaire introuvable.");
        }
        jsClick(btn.get());
    }

    /** Vrai si le champ est invalide (validation HTML5) ou si un message d'erreur est affiche. */
    protected boolean validationOrErrorShown(WebElement field) {
        return waitUntil(() -> {
            Boolean invalid = (Boolean) ((JavascriptExecutor) driver)
                    .executeScript("return arguments[0].checkValidity() === false;", field);
            return Boolean.TRUE.equals(invalid) || bodyText().matches(
                    "(?s).*(invalide|invalid|erreur|error|incorrect|obligatoire|required|requis|echec|échec).*");
        }, 8);
    }
}
