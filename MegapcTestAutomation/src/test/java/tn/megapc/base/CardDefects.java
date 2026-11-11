package tn.megapc.base;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regles de detection des defauts d'affichage des cartes produit
 * (aucune dependance Selenium : testable seule).
 *
 *  - prix affiche a 0 DT              (ex. « PNY NVIDIA DGX Spark ... 0 DT »)
 *  - image « AFFICHE EN COURS » ou autre image de remplacement a la place de la photo produit
 */
public final class CardDefects {

    private CardDefects() {
    }

    /** « 26 999 DT », « 864 DT », « 0 DT », « 0,00 DT », « 0.000 DT » */
    private static final Pattern PRICE = Pattern.compile("(\\d[\\d \\u00A0.]*)(?:,\\d+)?\\s*DT", Pattern.CASE_INSENSITIVE);

    /** Nom de fichier / alt / titre d'une image de remplacement. Texte deja normalise (minuscules, sans accents). */
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "affiche|en[\\s_+\\-]*cours|encours|placeholder|coming[\\s_+\\-]*soon|no[\\s_+\\-]*image"
            + "|image[\\s_+\\-]*(?:indisponible|manquante|par[\\s_+\\-]*defaut)|bientot[\\s_+\\-]*disponible");

    /** Vrai si le texte contient au moins un prix egal a zero (« 0 DT »). Ignore « 10 DT », « 2113 DT »... */
    public static boolean hasZeroPrice(String text) {
        if (text == null) {
            return false;
        }
        Matcher m = PRICE.matcher(text);
        while (m.find()) {
            String digits = m.group(1).replaceAll("[^0-9]", "");
            if (!digits.isEmpty() && Long.parseLong(digits) == 0L) {
                return true;
            }
        }
        return false;
    }

    /** Minuscules, sans accents, URL decodee (deux fois : les URLs /_next/image?url=... sont encodees). */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String out = s;
        for (int i = 0; i < 2; i++) {
            try {
                out = URLDecoder.decode(out, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                break;   // pourcentage isole : on garde le texte tel quel
            }
        }
        out = Normalizer.normalize(out, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return out.toLowerCase();
    }

    /** Vrai si l'URL / alt / titre decrit une image de remplacement (« AFFICHE EN COURS »...). */
    public static boolean looksLikePlaceholder(String srcAltTitle) {
        return PLACEHOLDER.matcher(normalize(srcAltTitle)).find();
    }

    /**
     * Cle d'identite d'une image : l'URL d'origine, sans les parametres de redimensionnement.
     * /_next/image?url=%2Fimg%2Fa.png&w=384&q=75  ->  /img/a.png
     */
    public static String imageKey(String src) {
        if (src == null || src.isBlank()) {
            return "";
        }
        String s = src;
        Matcher m = Pattern.compile("[?&]url=([^&]+)").matcher(s);
        if (m.find()) {
            s = normalize(m.group(1));
        } else {
            int q = s.indexOf('?');
            s = normalize(q >= 0 ? s.substring(0, q) : s);
        }
        return s;
    }
}
