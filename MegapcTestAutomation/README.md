# MegapcTestAutomation - Tests fonctionnels de https://megapc.tn/

Projet Maven : **Java 17 + Selenium 4.51 + TestNG 7.11** (20 tests automatises).

## Prerequis
- JDK 17 ou plus
- Google Chrome installe
- Maven (ou celui integre a IntelliJ)
- Acces Internet (Selenium Manager telecharge chromedriver automatiquement : aucun driver a installer)

## Ouvrir dans IntelliJ IDEA
1. `File > Open` puis choisir le dossier `MegapcTestAutomation` (ou son `pom.xml` > "Open as Project").
2. Attendre l'import Maven (dependances).
3. `File > Project Structure > Project` : SDK = JDK 17+.

## Executer
- IntelliJ : clic droit sur `testng.xml` > **Run** (ou sur `MegaPcTest` > Run).
- Terminal : `mvn clean test`
- Sans fenetre Chrome : `mvn clean test -Dheadless=true`

## Mode demonstration (pour le jury)
Par defaut, les tests sont ralentis (1500 ms entre les actions) : chaque element utilise est entoure
d'un cadre (rouge = action, vert = verification), la saisie se fait caractere par caractere et un
bandeau bleu en haut de la page indique le test et l'etape en cours.

| Besoin | Commande / reglage |
|---|---|
| Vitesse par defaut | `mvn clean test` |
| Plus lent (ex. 2,5 s) | `mvn clean test -Ddelay=2500` |
| Rapide (sans pauses) | `mvn clean test -Ddelay=0` |
| Dans IntelliJ | Run > Edit Configurations > TestNG > **VM options** : `-Ddelay=2500` |
| Sans fenetre (CI) | `mvn clean test -Dheadless=true -Ddelay=0` |

Groupes TestNG : `smoke` (parcours essentiels), `regression`, `negative` (cas d'erreur).
Exemple : `mvn clean test -Dgroups=smoke`.

## Resultats
| Livrable | Emplacement |
|---|---|
| Rapport d'execution HTML | `target/surefire-reports/index.html` et `emailable-report.html` |
| Captures d'ecran des echecs (PNG) | `target/screenshots/` |
| Captures des tests reussis (PNG) | `target/screenshots/passed/` |
| Traces d'exceptions (TXT) | `target/errors/` |

## Structure
```
pom.xml
testng.xml
src/test/java/tn/megapc/
  base/BaseTest.java              demarrage Chrome + methodes utilitaires
  listeners/ScreenshotListener.java   capture PNG + trace TXT sur echec
  base/CardDefects.java           regles de detection (prix 0 DT, image « AFFICHE EN COURS »)
  tests/MegaPcTest.java           les 20 tests
```

## Les 20 tests
01 HomePageLoading - 02 SearchExistingProduct - 03 SearchNonExistingProduct -
04 CategoryNavigation - 05 ProductDetailsDisplay - 06 AddToCart - 07 RemoveFromCart -
08 UpdateCartQuantity - 09 UserRegistrationInvalidEmail - 10 UserLoginEmptyFields -
11 UserLoginInvalidCredentials - 12 BrandShortcutFromHome - 13 FilterByBrand -
14 SortingByPriceAscending - 15 NoPlaceholderProductImages - 16 ContactInformationInFooter -
17 SocialMediaLinks - 18 NoZeroPriceProducts - 19 FooterLinksRedirection -
20 CheckoutProcessAccess

## Tests en echec volontaire (defauts reels du site)
Deux tests detectent des defauts constates sur megapc.tn et **echouent tant que le site n'est pas corrige** :

| Test | Defaut | Ce que montre la capture |
|---|---|---|
| 15 `testNoPlaceholderProductImages` | image « AFFICHE EN COURS » (ou image cassee / identique pour plusieurs produits) a la place de la photo produit | la carte fautive, encadree en rouge |
| 18 `testNoZeroPriceProducts` | produit affiche a « 0 DT » (ex. PNY NVIDIA DGX Spark) | la carte fautive, encadree en rouge |

Pages controlees : accueil, `/shop/ECRANS/ECRANS%20GAMING`, `/shop/PC%20PORTABLE/PC%20PORTABLE%20GAMER`
(constante `CARD_PAGES` dans `BaseTest`). Le test s'arrete a la premiere page fautive et liste tous les
produits concernes dans le message d'echec. Capture : `target/screenshots/<test>_<date>.png`, trace : `target/errors/`.
Groupe TestNG `defect` : `mvn clean test -Dgroups=defect` pour ne lancer que ces deux tests.

## Important : tests SKIPPED
megapc.tn est une application Next.js dont le DOM est genere par JavaScript.
Les tests 06, 07, 08, 09, 10, 11, 14, 20 reposent sur des locators generiques
(texte "Ajouter", "Panier", "Connexion", etc.). Si un element est introuvable, le test est
marque **SKIPPED** avec un message precis (au lieu de "passer" a tort).
Pour les activer : clic droit > Inspecter sur le site, puis adapter le locator indique
dans `BaseTest` (`addFirstProductToCart`, `openCart`, `openLoginPage`).

## Jeux de donnees
`testdata/jeux_de_donnees.csv` (separateur `;`) documente les donnees utilisees par les tests
(termes de recherche, identifiants fictifs, marques, URLs). Les memes valeurs sont ecrites dans `MegaPcTest.java`.

## Reduire les tests SKIPPED (export du DOM)
Quand `addFirstProductToCart()`, `openCart()` ou `openLoginPage()` ne trouvent pas leur element, le test est
SKIPPED **et** la liste des liens / boutons / champs de la page est ecrite dans `target/dom-dump/<test>_<page>.txt`
(href, texte, aria-label, classe). Envoyer ce fichier permet d'ecrire le locator exact au lieu de le deviner.
`BaseTest.buttonWithText(...)` ne cible que de vrais boutons (evite de cliquer sur le lien « Panier » de l'en-tete).

## Resultat attendu : 20 tests = PASSED + FAILED + IGNORED
| Statut | Nb | Tests |
|---|---|---|
| FAILED (defauts reels du site) | 2 | 15 `NoPlaceholderProductImages`, 18 `NoZeroPriceProducts` |
| IGNORED (SkipException volontaire) | 2 | 09 `UserRegistrationInvalidEmail`, 20 `CheckoutProcessAccess` |
| PASSED | 16 | tous les autres |

Les 2 tests ignores le sont **volontairement** (site de production : pas de creation de compte ni de commande).
Pour les executer quand meme : `mvn clean test -Drun.ignored=true`.
Si un autre test apparait SKIPPED, c'est un locator introuvable : voir `target/dom-dump/`.
Le resultat PASSED depend du site reel : je n'ai pas pu l'executer depuis mon environnement.
