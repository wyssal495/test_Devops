package tn.megapc.tests;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;
import tn.megapc.base.BaseTest;
import tn.megapc.base.CardDefects;
import tn.megapc.listeners.ScreenshotListener;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 20 tests fonctionnels de https://megapc.tn/ (TestNG + Selenium).
 *
 * - Mode demonstration : pauses, elements surlignes, saisie lente, bandeau d'etape
 *   (regler la vitesse avec -Ddelay=<ms> ; -Ddelay=0 = execution rapide).
 * - Groupes TestNG : smoke (parcours essentiels), regression, negative (cas d'erreur).
 * - Les tests dont la fonctionnalite est introuvable levent une SkipException explicite
 *   (statut SKIPPED) au lieu de "passer" a tort.
 * - SoftAssert quand plusieurs verifications independantes sont faites dans un meme test.
 */
@Listeners(ScreenshotListener.class)
public class MegaPcTest extends BaseTest {

    // 01 ------------------------------------------------------------------
    @Test(priority = 1, groups = {"smoke"}, description = "Chargement de la page d'accueil")
    public void testHomePageLoading() {
        step("Verification du titre, du logo et des sections de l'accueil");
        SoftAssert soft = new SoftAssert();

        soft.assertTrue(driver.getTitle().toLowerCase().contains("pc gamer"),
                "Titre inattendu : " + driver.getTitle());

        Optional<WebElement> logo = firstDisplayed(By.cssSelector("img[alt='Logo']"));
        soft.assertTrue(logo.isPresent(), "Le logo n'est pas visible");
        logo.ifPresent(e -> highlight(e, GREEN));

        soft.assertTrue(waitUntil(() -> bodyText().contains("shop par marque"), 10),
                "La section « SHOP PAR MARQUE » est absente");
        soft.assertTrue(bodyText().contains("produits sp"),
                "La section « PRODUITS SPECIAUX » est absente");
        soft.assertFalse(productLinks().isEmpty(), "Aucun produit affiche sur l'accueil");
        soft.assertAll();
    }

    // 02 ------------------------------------------------------------------
    @Test(priority = 2, groups = {"smoke"}, description = "Recherche d'un produit existant")
    public void testSearchExistingProduct() {
        searchFor("RTX");
        step("Attente des resultats de recherche");
        waitForProducts();

        Assert.assertTrue(driver.getCurrentUrl().toLowerCase().contains("search"),
                "L'URL ne correspond pas a une page de recherche : " + driver.getCurrentUrl());
        boolean relevant = productLinks().stream().anyMatch(a ->
                (a.getText() + " " + a.getAttribute("href")).toLowerCase().contains("rtx"));
        Assert.assertTrue(relevant, "Aucun resultat ne correspond au terme 'RTX'");
        highlight(productLinks().get(0), GREEN);
    }

    // 03 ------------------------------------------------------------------
    @Test(priority = 3, groups = {"negative"}, description = "Recherche d'un produit inexistant")
    public void testSearchNonExistingProduct() {
        searchFor("xyz123456789");
        step("Verification qu'aucun produit n'est affiche");
        waitForPage();

        boolean productFound;
        try {
            new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(6))
                    .until(d -> !productLinks().isEmpty());
            productFound = true;
        } catch (TimeoutException e) {
            productFound = false;
        }
        Assert.assertFalse(productFound, "Des produits sont affiches pour un terme inexistant");
        Assert.assertTrue(driver.getCurrentUrl().toLowerCase().contains("xyz123456789"),
                "La recherche n'a pas ete executee : " + driver.getCurrentUrl());
        pause(1.0);
    }

    // 04 ------------------------------------------------------------------
    @Test(priority = 4, groups = {"smoke"}, description = "Navigation vers la categorie PC Portable")
    public void testCategoryNavigation() {
        step("Clic sur la categorie « Pc portable »");
        By categoryLink = By.xpath("//a[contains(@href,'Nos-Categories/PC') and "
                + "contains(translate(@href,'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ'),'PORTABLE')]");
        WebElement link = driver.findElements(categoryLink).stream().findFirst()
                .orElseThrow(() -> new AssertionError("Lien 'Pc portable' introuvable"));
        jsClick(link);
        waitForPage();

        SoftAssert soft = new SoftAssert();
        soft.assertTrue(driver.getCurrentUrl().toUpperCase().contains("PORTABLE"),
                "L'URL n'a pas change vers la categorie : " + driver.getCurrentUrl());
        soft.assertTrue(waitUntil(() -> bodyText().contains("pc portable"), 15),
                "La page de la categorie PC PORTABLE ne s'affiche pas");
        soft.assertTrue(waitUntil(() -> !driver.findElements(By.xpath(
                "//a[contains(@href,'PC%20PORTABLE%20GAMER') or contains(@href,'PC PORTABLE GAMER')]")).isEmpty(), 10),
                "La sous-categorie « PC PORTABLE GAMER » n'est pas proposee");
        soft.assertAll();
    }

    // 05 ------------------------------------------------------------------
    @Test(priority = 5, groups = {"smoke"}, description = "Affichage de la fiche produit")
    public void testProductDetailsDisplay() {
        step("Ouverture de la categorie PC PORTABLE GAMER");
        open("/shop/PC%20PORTABLE/PC%20PORTABLE%20GAMER");
        waitForProducts();
        step("Clic sur le premier produit de la liste");
        jsClick(productLinks().get(0));
        waitForPage();

        Assert.assertTrue(driver.getCurrentUrl().contains("/shop/product/"),
                "Ce n'est pas une fiche produit : " + driver.getCurrentUrl());
        step("Verification du titre et du prix du produit");
        Optional<WebElement> title = waitForDisplayed(By.tagName("h1"), 15);
        Assert.assertTrue(title.isPresent() && !title.get().getText().isBlank(),
                "Le titre (h1) du produit n'est pas affiche");
        highlight(title.get(), GREEN);
        String t = bodyText();
        Assert.assertTrue(t.contains("dt") || t.contains("prix"),
                "Aucun prix (ou « Prix sur demande ») sur la fiche produit");
    }

    // 06 ------------------------------------------------------------------
    @Test(priority = 6, groups = {"regression"}, description = "Ajout d'un produit au panier")
    public void testAddToCart() {
        addFirstProductToCart();

        step("Verification que le produit est dans le panier");
        boolean confirmed = waitUntil(() -> {
            String t = bodyText();
            return t.contains("ajouté") || t.contains("ajoute") || t.contains("added")
                    || (t.contains("sous-total") && !cartLooksEmpty());
        }, 6);
        if (!confirmed) {
            openCart();   // ouvre le tiroir « Panier » et controle son contenu
            confirmed = waitUntil(() -> !cartLooksEmpty() && bodyText().contains("sous-total"), 6);
        }
        Assert.assertTrue(confirmed, "Le panier est toujours vide apres l'ajout du produit");
    }

    // 07 ------------------------------------------------------------------
    @Test(priority = 7, groups = {"regression"}, description = "Suppression d'un article du panier")
    public void testRemoveFromCart() {
        addFirstProductToCart();
        openCart();

        int before = cartItemCount();
        String subtotalBefore = cartSubtotal();
        System.out.println("  Panier avant suppression : " + before + " article(s), sous-total " + subtotalBefore);
        Assert.assertFalse(cartLooksEmpty(), "Le panier est deja vide AVANT la suppression : l'ajout du produit n'a pas fonctionne");

        step("Suppression de l'article");
        Optional<WebElement> remove = cartRemoveControl();
        if (remove.isEmpty()) {
            dumpCartHtml("tiroir");
            throw new SkipException("Bouton de suppression introuvable dans le panier (voir target/dom-dump/*.html) : adapter BaseTest.cartRemoveControl().");
        }
        jsClick(remove.get());

        step("Verification que l'article a ete retire (nombre d'articles / sous-total)");
        final int countBefore = before;
        boolean removed = waitUntil(() -> {
            if (cartLooksEmpty()) {
                return true;
            }
            int now = cartItemCount();
            return countBefore > 0 && now >= 0 && now < countBefore;
        }, 10);
        if (!removed) {
            dumpCartHtml("apres_suppression");
        }
        Assert.assertTrue(removed, "L'article n'a pas ete supprime : articles avant=" + before + ", apres="
                + cartItemCount() + " ; sous-total avant=" + subtotalBefore + ", apres=" + cartSubtotal());
    }

    // 08 ------------------------------------------------------------------
    @Test(priority = 8, groups = {"regression"}, description = "Mise a jour de la quantite dans le panier")
    public void testUpdateCartQuantity() {
        addFirstProductToCart();
        openCart();

        step("Passage de la quantite a 2");
        By qtyLocator = By.cssSelector("input[type='number'], input[name*='qty' i], input[name*='quant' i]");
        Optional<WebElement> plus = firstDisplayed(clickableWithText("+"));
        Optional<WebElement> qty = firstDisplayed(qtyLocator);

        if (plus.isPresent()) {
            jsClick(plus.get());
        } else if (qty.isPresent()) {
            highlight(qty.get());
            qty.get().sendKeys(Keys.chord(Keys.CONTROL, "a"), "2", Keys.TAB);
        } else {
            throw new SkipException("Controle de quantite introuvable : adapter le locator.");
        }

        boolean updated = waitUntil(() -> {
            Optional<WebElement> q = firstDisplayed(qtyLocator);
            if (q.isPresent()) {
                return "2".equals(q.get().getAttribute("value"));
            }
            return !driver.findElements(By.xpath("//*[normalize-space(text())='2']")).isEmpty();
        }, 10);
        Assert.assertTrue(updated, "La quantite n'est pas passee a 2");
    }

    // 09 ------------------------------------------------------------------
    @Test(priority = 9, groups = {"negative"}, description = "Inscription avec un email invalide")
    public void testUserRegistrationInvalidEmail() {
        ignoreByDesign("l'inscription est testee sur un site de production reel : eviter de creer / soumettre des comptes");
        openLoginPage();

        step("Acces au formulaire d'inscription");
        Optional<WebElement> registerLink = firstDisplayed(
                clickableWithText("inscri", "register", "sign up", "creer un compte", "nouveau client"));
        registerLink.ifPresent(this::jsClick);

        Optional<WebElement> email = waitForDisplayed(
                By.cssSelector("input[type='email'], input[name*='mail' i]"), 5);
        if (email.isEmpty()) {
            throw new SkipException("Formulaire d'inscription introuvable : adapter le locator.");
        }
        step("Saisie d'un email invalide (sans @)");
        type(email.get(), "email-invalide-sans-arobase");
        clickSubmit();

        step("Verification du message d'erreur");
        Assert.assertTrue(validationOrErrorShown(email.get()),
                "Aucune erreur de validation pour un email invalide");
    }

    // 10 ------------------------------------------------------------------
    @Test(priority = 10, groups = {"negative"}, description = "Connexion avec champs vides")
    public void testUserLoginEmptyFields() {
        openLoginPage();
        WebElement password = firstDisplayed(By.cssSelector("input[type='password']")).get();

        step("Validation du formulaire avec des champs vides");
        clickSubmit();

        step("Verification du message d'erreur");
        Assert.assertTrue(validationOrErrorShown(password),
                "Aucun message d'erreur / validation pour des champs vides");
        Assert.assertTrue(firstDisplayed(By.cssSelector("input[type='password']")).isPresent(),
                "L'utilisateur ne devrait pas etre connecte avec des champs vides");
    }

    // 11 ------------------------------------------------------------------
    @Test(priority = 11, groups = {"negative"}, description = "Connexion avec identifiants incorrects")
    public void testUserLoginInvalidCredentials() {
        openLoginPage();

        Optional<WebElement> user = firstDisplayed(By.cssSelector(
                "input[type='email'], input[name*='mail' i], input[name*='user' i], input[type='text']"));
        if (user.isEmpty()) {
            throw new SkipException("Champ identifiant introuvable : adapter le locator.");
        }
        step("Saisie de faux identifiants");
        type(user.get(), "wrong@test.tn");
        WebElement password = firstDisplayed(By.cssSelector("input[type='password']")).get();
        type(password, "pass123");
        clickSubmit();

        step("Verification du message d'erreur et de l'absence de connexion");
        Assert.assertTrue(waitUntil(() -> bodyText().matches(
                "(?s).*(invalide|invalid|incorrect|erreur|error|echec|échec|introuvable|not found).*"), 10),
                "Aucun message d'erreur pour des identifiants incorrects");
        Assert.assertTrue(firstDisplayed(By.cssSelector("input[type='password']")).isPresent(),
                "Connexion acceptee avec de faux identifiants !");
    }

    // 12 ------------------------------------------------------------------
    @Test(priority = 12, groups = {"regression"}, description = "Acces aux produits d'une marque depuis l'accueil")
    public void testBrandShortcutFromHome() {
        step("Clic sur la tuile de marque ASUS (SHOP PAR MARQUE)");
        WebElement asus = driver.findElements(By.cssSelector("a[href$='/shop/search/ASUS']")).stream()
                .findFirst().orElseThrow(() -> new AssertionError("Tuile de marque ASUS introuvable"));
        jsClick(asus);
        waitForPage();

        Assert.assertTrue(driver.getCurrentUrl().contains("/shop/search/ASUS"),
                "URL inattendue : " + driver.getCurrentUrl());
        waitForProducts();
        Assert.assertTrue(productLinks().size() > 0, "Aucun produit ASUS affiche");
        highlight(productLinks().get(0), GREEN);
    }

    // 13 ------------------------------------------------------------------
    @Test(priority = 13, groups = {"regression"}, description = "Filtrage par marque (?marque=GIGABYTE)")
    public void testFilterByBrand() {
        step("Ouverture des ecrans gaming filtres sur la marque GIGABYTE");
        open("/shop/ECRANS/ECRANS%20GAMING?marque=GIGABYTE");
        waitForProducts();

        step("Controle de la marque de chaque produit affiche");
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (WebElement a : productLinks()) {
            String info = (a.getText() + " " + a.getAttribute("href")).toLowerCase();
            checked++;
            if (!info.contains("gigabyte")) {
                offenders.add(info.length() > 80 ? info.substring(0, 80) : info);
            }
        }
        SoftAssert soft = new SoftAssert();
        soft.assertTrue(checked > 0, "Aucun produit affiche");
        soft.assertTrue(offenders.isEmpty(), "Produits d'une autre marque affiches : " + offenders);
        soft.assertTrue(driver.getCurrentUrl().toLowerCase().contains("marque=gigabyte"),
                "Le filtre a disparu de l'URL : " + driver.getCurrentUrl());
        soft.assertAll();
        pause(1.0);
    }

    // 14 ------------------------------------------------------------------
    @Test(priority = 14, groups = {"regression"}, description = "Tri des produits par prix croissant")
    public void testSortingByPriceAscending() {
        step("Ouverture de PC PORTABLE GAMER");
        open("/shop/PC%20PORTABLE/PC%20PORTABLE%20GAMER");
        waitForProducts();

        step("Selection du tri par prix croissant");
        Optional<WebElement> select = firstDisplayed(By.tagName("select"));
        if (select.isPresent()) {
            highlight(select.get());
            Select s = new Select(select.get());
            String target = s.getOptions().stream()
                    .map(WebElement::getText)
                    .filter(t -> t.toLowerCase().matches(".*(croissant|asc|low).*"))
                    .findFirst()
                    .orElseThrow(() -> new SkipException("Option de tri 'prix croissant' introuvable."));
            s.selectByVisibleText(target);
        } else {
            Optional<WebElement> btn = firstDisplayed(
                    clickableWithText("prix croissant", "prix: croissant", "low to high"));
            if (btn.isEmpty()) {
                throw new SkipException("Controle de tri introuvable : adapter le locator.");
            }
            jsClick(btn.get());
        }

        step("Verification de l'ordre croissant des prix");
        boolean sorted = waitUntil(() -> {
            List<Long> p = extractPrices();
            if (p.size() < 2) {
                return false;
            }
            for (int i = 1; i < p.size(); i++) {
                if (p.get(i) < p.get(i - 1)) {
                    return false;
                }
            }
            return true;
        }, 12);
        Assert.assertTrue(sorted, "Les prix ne sont pas tries par ordre croissant : " + extractPrices());
    }

    // 15 ------------------------------------------------------------------
    /**
     * DEFAUT CONNU DU SITE (test volontairement en ECHEC tant que le site n'est pas corrige) :
     * des cartes produit / bundles affichent l'image « AFFICHE EN COURS » au lieu de la photo du produit.
     * La carte fautive est encadree en rouge ; la capture est enregistree dans target/screenshots/.
     */
    @Test(priority = 15, groups = {"regression", "defect"},
            description = "Aucun produit ne doit afficher l'image « AFFICHE EN COURS » a la place de sa photo")
    public void testNoPlaceholderProductImages() {
        for (String page : CARD_PAGES) {
            step("Controle des images produit : " + page);
            open(page);
            waitForProducts();
            scrollWholePage();

            List<WebElement> cards = visibleProductCards();
            java.util.Map<String, Integer> shared = countSharedImages(cards);
            List<String> offenders = new ArrayList<>();
            WebElement first = null;
            for (WebElement card : cards) {
                String reason = placeholderReason(card, shared);
                if (reason != null) {
                    offenders.add(cardLabel(card) + " -> " + reason);
                    markDefect(card);
                    if (first == null) {
                        first = card;
                    }
                }
            }
            System.out.println("  " + page + " : " + cards.size() + " produits controles, "
                    + offenders.size() + " image(s) de remplacement");
            if (first != null) {
                scrollToCenter(first);   // la capture d'echec montre la carte fautive
                Assert.fail("Image « AFFICHE EN COURS » (ou image absente) a la place de la photo produit sur "
                        + page + " (" + offenders.size() + " produit(s)) : " + offenders);
            }
        }
    }

    // 16 ------------------------------------------------------------------
    @Test(priority = 16, groups = {"regression"}, description = "Coordonnees dans le pied de page")
    public void testContactInformationInFooter() {
        step("Defilement vers le pied de page et verification des coordonnees");
        ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("window.scrollTo(0, document.body.scrollHeight);");
        pause(1.5);

        String source = driver.getPageSource().toLowerCase();
        SoftAssert soft = new SoftAssert();
        soft.assertTrue(source.contains("28777101"), "Numero de telephone absent du pied de page");
        soft.assertTrue(source.contains("ben arous"), "Adresse absente du pied de page");
        soft.assertTrue(source.contains("copyright"), "Mention de copyright absente");
        soft.assertAll();
    }

    // 17 ------------------------------------------------------------------
    @Test(priority = 17, groups = {"regression"}, description = "Liens reseaux sociaux")
    public void testSocialMediaLinks() {
        step("Defilement vers le pied de page et controle des liens sociaux");
        ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("window.scrollTo(0, document.body.scrollHeight);");
        pause(1.0);

        SoftAssert soft = new SoftAssert();
        for (String[] s : new String[][]{
                {"facebook.com/GoMegaPC", "Facebook"},
                {"instagram.com/megapc_official", "Instagram"},
                {"youtube.com/channel", "YouTube"}}) {
            List<WebElement> found = driver.findElements(By.cssSelector("a[href*='" + s[0] + "']"));
            soft.assertFalse(found.isEmpty(), "Lien " + s[1] + " absent");
            if (!found.isEmpty()) {
                highlight(found.get(found.size() - 1), GREEN);
            }
        }
        soft.assertAll();
    }

    // 18 ------------------------------------------------------------------
    /**
     * DEFAUT CONNU DU SITE (test volontairement en ECHEC tant que le site n'est pas corrige) :
     * un produit est affiche au prix « 0 DT » (ex. PNY NVIDIA DGX Spark).
     * La carte fautive est encadree en rouge ; la capture est enregistree dans target/screenshots/.
     */
    @Test(priority = 18, groups = {"regression", "defect"},
            description = "Aucun produit ne doit etre affiche au prix « 0 DT »")
    public void testNoZeroPriceProducts() {
        for (String page : CARD_PAGES) {
            step("Controle des prix : " + page);
            open(page);
            waitForProducts();
            scrollWholePage();

            List<WebElement> cards = visibleProductCards();
            List<String> offenders = new ArrayList<>();
            WebElement first = null;
            for (WebElement card : cards) {
                if (CardDefects.hasZeroPrice(card.getText())) {
                    offenders.add(cardLabel(card));
                    markDefect(card);
                    if (first == null) {
                        first = card;
                    }
                }
            }
            System.out.println("  " + page + " : " + cards.size() + " produits controles, "
                    + offenders.size() + " au prix 0 DT");
            if (first != null) {
                scrollToCenter(first);   // la capture d'echec montre la carte fautive
                Assert.fail("Prix « 0 DT » affiche sur " + page + " (" + offenders.size()
                        + " produit(s)) : " + offenders);
            }
        }
    }

    // 19 ------------------------------------------------------------------
    @Test(priority = 19, groups = {"regression"}, description = "Redirection du lien Conditions")
    public void testFooterLinksRedirection() throws Exception {
        step("Clic sur le lien « Conditions » du pied de page");
        WebElement link = driver.findElements(By.cssSelector("a[href$='/conditions']")).stream()
                .findFirst().orElseThrow(() -> new AssertionError("Lien 'Conditions' introuvable"));
        String href = link.getAttribute("href");
        jsClick(link);
        waitForPage();

        Assert.assertTrue(driver.getCurrentUrl().contains("/conditions"),
                "Redirection incorrecte : " + driver.getCurrentUrl());

        step("Verification du statut HTTP de la page");
        HttpURLConnection c = (HttpURLConnection) new URL(href).openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        Assert.assertEquals(c.getResponseCode(), 200, "Statut HTTP inattendu pour " + href);
    }

    // 20 ------------------------------------------------------------------
    @Test(priority = 20, groups = {"regression"}, description = "Acces au processus de commande")
    public void testCheckoutProcessAccess() {
        ignoreByDesign("le tunnel de commande d'un site de production reel n'est pas automatise (risque de commande reelle)");
        addFirstProductToCart();
        openCart();

        step("Clic sur « Commander »");
        Optional<WebElement> order = waitForDisplayed(
                clickableWithText("commander", "passer la commande", "checkout", "valider"), 8);
        if (order.isEmpty()) {
            throw new SkipException("Bouton 'Commander' introuvable : adapter le locator.");
        }
        jsClick(order.get());
        waitForPage();

        step("Verification de l'etape de commande / livraison");
        boolean reached = waitUntil(() -> {
            String u = driver.getCurrentUrl().toLowerCase();
            String t = bodyText();
            return u.contains("checkout") || u.contains("commande") || u.contains("order")
                    || t.contains("livraison") || t.contains("adresse");
        }, 10);
        Assert.assertTrue(reached, "L'etape de commande / livraison n'est pas affichee");
    }
}
