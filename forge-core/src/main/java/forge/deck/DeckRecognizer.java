/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.deck;

import com.google.common.collect.Lists;
import forge.StaticData;
import forge.card.CardEdition;
import forge.card.CardType;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.util.TextUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * DeckRecognizer class.
 * </p>
 * 
 */
public class DeckRecognizer {
    /**
     * The Enum TokenType.
     */
    public enum TokenType {
        LEGAL_CARD_REQUEST,
        ILLEGAL_CARD_REQUEST,
        INVALID_CARD_REQUEST,
        UNKNOWN_CARD_REQUEST,
        UNKNOWN_TEXT,
        DECK_NAME,
        COMMENT,
        DECK_SECTION_NAME,
        CARD_TYPE,
        CARD_RARITY,
        CARD_CMC,
        MANA_COLOUR
    }

    /**
     * The Class Token.
     */
    public static class Token {
        private final TokenType type;
        private final int number;
        private final String text;
        // only used for card tokens
        private PaperCard card = null;
        private DeckSection tokenSection = null;

        public static Token KnownCard(final PaperCard theCard, final int count,
                                      DeckSection section) {
            return new Token(theCard, TokenType.LEGAL_CARD_REQUEST, count, section);
        }

        public static Token IllegalCard(final PaperCard theCard, final int count) {
            return new Token(theCard, TokenType.ILLEGAL_CARD_REQUEST, count, null);
        }

        public static Token InvalidCard(final PaperCard theCard, final int count) {
            return new Token(theCard, TokenType.INVALID_CARD_REQUEST, count, null);
        }

        public static Token UnknownCard(final String cardName, final String setCode, final int count) {
            String ttext = setCode == null || setCode.equals("") ? cardName :
                    String.format("%s (%s)", cardName, setCode);
            return new Token(TokenType.UNKNOWN_CARD_REQUEST, count, ttext);
        }

        public static Token DeckSection(final String sectionName0){
            String sectionName = sectionName0.toLowerCase();
            if (sectionName.equals("side") || sectionName.contains("sideboard"))
                return new Token(TokenType.DECK_SECTION_NAME, DeckSection.Sideboard.name());
            if (sectionName.equals("main") || sectionName.contains("card")
                    || sectionName.equals("mainboard") || sectionName.equals("deck"))
                return new Token(TokenType.DECK_SECTION_NAME, DeckSection.Main.name());
            if (sectionName.equals("avatar"))
                return new Token(TokenType.DECK_SECTION_NAME, DeckSection.Avatar.name());
            if (sectionName.equals("commander"))
                return new Token(TokenType.DECK_SECTION_NAME, DeckSection.Commander.name());
            if (sectionName.equals("schemes"))
                return new Token(TokenType.DECK_SECTION_NAME, DeckSection.Schemes.name());
            if (sectionName.equals("conspiracy"))
                return new Token(TokenType.DECK_SECTION_NAME, DeckSection.Conspiracy.name());
            if (sectionName.equals("planes"))
                return new Token(TokenType.DECK_SECTION_NAME, DeckSection.Planes.name());
            return null;
        }

        private Token(final PaperCard tokenCard, final TokenType type1,
                      final int count, final DeckSection section) {
            this.number = count;
            this.type = type1;
            this.text = String.format("%s (%s)", tokenCard.getName(), tokenCard.getEdition());
            this.card = tokenCard;
            this.tokenSection = section;
        }

        public Token(final TokenType type1, final int count, final String message) {
            this.number = count;
            this.type = type1;
            this.text = message;
        }

        public Token(final TokenType type1, final String message) {
            this(type1, 0, message);
        }

        public final String getText() {
            return this.text;
        }

        public final PaperCard getCard() {
            return this.card;
        }

        public final TokenType getType() {
            return this.type;
        }

        public final int getNumber() {
            return this.number;
        }

        public final DeckSection getTokenSection() { return this.tokenSection; }

        public boolean isCardToken() {
            return (this.type == TokenType.LEGAL_CARD_REQUEST ||
                    this.type == TokenType.ILLEGAL_CARD_REQUEST ||
                    this.type == TokenType.INVALID_CARD_REQUEST );
        }

        public String getKey(){
            if (this.isCardToken()) {
                if (this.tokenSection != null)
                    return String.format("%s-%s-%s-%s-%s",
                                this.card.getName(), this.card.getEdition(),
                                this.card.getCollectorNumber(),
                                this.getType().name().toLowerCase(),
                                this.tokenSection.name());
                return String.format("%s-%s-%s-%s",
                        this.card.getName(), this.card.getEdition(),
                        this.card.getCollectorNumber(),
                        this.getType().name().toLowerCase());
            }
            return null;
        }

        public static class TokenKey {
            public String cardName;
            public String setCode;
            public String collectorNumber;
            public String deckSection;
            public String typeName;
        }

        public static TokenKey parseTokenKey(String key){
            String[] keyInfo = TextUtil.split(key, '-');
            TokenKey tokenKey = new TokenKey();
            tokenKey.cardName = keyInfo[0];
            tokenKey.setCode = keyInfo[1];
            tokenKey.collectorNumber = keyInfo[2];
            tokenKey.typeName = keyInfo[3];
            if (keyInfo.length > 4)
                tokenKey.deckSection = keyInfo[4];
            else
                tokenKey.deckSection = null;
            return  tokenKey;
        }
    }

    // Utility Constants
    private static final Pattern SEARCH_SINGLE_SLASH = Pattern.compile("(?<=[^/])\\s*/\\s*(?=[^/])");
    private static final String DOUBLE_SLASH = "//";
    private static final String LINE_COMMENT_DELIMITER_OR_MD_HEADER = "#";
    private static final String ASTERISK = "* ";  // Note the blank space after asterisk!

    // Core Matching Patterns (initialised in Constructor)
    public static final String REGRP_DECKNAME = "deckName";
    public static final String REX_DECK_NAME =
            String.format("^(\\/\\/\\s*)?(?<pre>(deck|name(\\s)?))(\\:|=)\\s*(?<%s>([a-zA-Z0-9',\\/\\-\\s\\)\\]\\(\\[\\#]+))\\s*(.*)$",
                    REGRP_DECKNAME);
    public static final Pattern DECK_NAME_PATTERN = Pattern.compile(REX_DECK_NAME, Pattern.CASE_INSENSITIVE);

    public static final String REGRP_TOKEN = "token";
    public static final String REX_NOCARD = String.format("^(?<pre>[^a-zA-Z]*)\\s*(?<title>(\\w+[:]\\s*))?(?<%s>[a-zA-Z]+)(?<post>[^a-zA-Z]*)?$", REGRP_TOKEN);
    public static final String REX_CMC = String.format("^(?<pre>[^a-zA-Z]*)\\s*(?<%s>(C(M)?C(\\s)?\\d{1,2}))(?<post>[^\\d]*)?$", REGRP_TOKEN);
    public static final String REX_RARITY = String.format("^(?<pre>[^a-zA-Z]*)\\s*(?<%s>((un)?common|(mythic)?\\s*(rare)?|land))(?<post>[^a-zA-Z]*)?$", REGRP_TOKEN);
    public static final String REX_COLOUR = String.format("^(?<pre>[^a-zA-Z]*)\\s*(?<%s>(white|blue|black|red|green|colo(u)?rless|multicolo(u)?r))(?<post>[^a-zA-Z]*)?$", REGRP_TOKEN);
    public static final Pattern NONCARD_PATTERN = Pattern.compile(REX_NOCARD, Pattern.CASE_INSENSITIVE);
    public static final Pattern CMC_PATTERN = Pattern.compile(REX_CMC, Pattern.CASE_INSENSITIVE);
    public static final Pattern CARD_RARITY_PATTERN = Pattern.compile(REX_RARITY, Pattern.CASE_INSENSITIVE);
    public static final Pattern MANA_PATTERN = Pattern.compile(REX_COLOUR, Pattern.CASE_INSENSITIVE);

    public static final String REGRP_SET = "setcode";
    public static final String REGRP_COLLNR = "collnr";
    public static final String REGRP_CARD = "cardname";
    public static final String REGRP_CARDNO = "count";

    public static final String REX_CARD_NAME = String.format("(\\[)?(?<%s>[a-zA-Z0-9',\\.:!\\+\\\"\\/\\-\\s]+)(\\])?", REGRP_CARD);
    public static final String REX_SET_CODE = String.format("(?<%s>[a-zA-Z0-9_]{2,7})", REGRP_SET);
    public static final String REX_COLL_NUMBER = String.format("(?<%s>\\*?[0-9A-Z]+\\S?[A-Z]*)", REGRP_COLLNR);
    public static final String REX_CARD_COUNT = String.format("(?<%s>[\\d]{1,2})(?<mult>x)?", REGRP_CARDNO);
    // EXTRA
    public static final String REGRP_FOIL_GFISH = "foil";
    private static final String REX_FOIL_MTGGOLDFISH = String.format(
            "(?<%s>\\(F\\))?", REGRP_FOIL_GFISH);
    // XMage Sideboard indicator - pushed a bit further with deck section indication
    public static final String REGRP_DECK_SEC_XMAGE_STYLE = "decsec";
    private static final String REX_DECKSEC_XMAGE = String.format(
            "(?<%s>(MB|SB|CM))", REGRP_DECK_SEC_XMAGE_STYLE);

    // 1. Card-Set Request (Amount?, CardName, Set)
    public static final String REX_CARD_SET_REQUEST = String.format(
            "(%s\\s*:\\s*)?(%s\\s)?\\s*%s\\s*(\\s|\\||\\(|\\[|\\{)%s(\\s|\\)|\\]|\\})?\\s*%s",
            REX_DECKSEC_XMAGE, REX_CARD_COUNT, REX_CARD_NAME, REX_SET_CODE, REX_FOIL_MTGGOLDFISH);
    public static final Pattern CARD_SET_PATTERN = Pattern.compile(REX_CARD_SET_REQUEST);
    // 2. Set-Card Request (Amount?, Set, CardName)
    public static final String REX_SET_CARD_REQUEST = String.format(
            "(%s\\s*:\\s*)?(%s\\s)?\\s*(\\(|\\[|\\{)?%s(\\s+|\\)|\\]|\\}|\\|)\\s*%s\\s*%s\\s*",
            REX_DECKSEC_XMAGE, REX_CARD_COUNT, REX_SET_CODE, REX_CARD_NAME, REX_FOIL_MTGGOLDFISH);
    public static final Pattern SET_CARD_PATTERN = Pattern.compile(REX_SET_CARD_REQUEST);
    // 3. Full-Request (Amount?, CardName, Set, Collector Number|Art Index) - MTGArena Format
    public static final String REX_FULL_REQUEST_CARD_SET = String.format(
            "(%s\\s*:\\s*)?(%s\\s)?\\s*%s\\s*(\\||\\(|\\[|\\{|\\s)%s(\\s|\\)|\\]|\\})?\\s+%s\\s*%s\\s*",
            REX_DECKSEC_XMAGE, REX_CARD_COUNT, REX_CARD_NAME, REX_SET_CODE, REX_COLL_NUMBER, REX_FOIL_MTGGOLDFISH);
    public static final Pattern CARD_SET_COLLNO_PATTERN = Pattern.compile(REX_FULL_REQUEST_CARD_SET);
    // 4. Full-Request (Amount?, Set, CardName, Collector Number|Art Index) - Alternative for flexibility
    public static final String REX_FULL_REQUEST_SET_CARD = String.format(
            "^(%s\\s*:\\s*)?(%s\\s)?\\s*(\\(|\\[|\\{)?%s(\\s+|\\)|\\]|\\}|\\|)\\s*%s\\s+%s\\s*%s$",
            REX_DECKSEC_XMAGE, REX_CARD_COUNT, REX_SET_CODE, REX_CARD_NAME, REX_COLL_NUMBER, REX_FOIL_MTGGOLDFISH);
    public static final Pattern SET_CARD_COLLNO_PATTERN = Pattern.compile(REX_FULL_REQUEST_SET_CARD);
    // 5. (MTGGoldfish mostly) (Amount?, Card Name, <Collector Number>, Set)
    public static final String REX_FULL_REQUEST_CARD_COLLNO_SET = String.format(
            "^(%s\\s*:\\s*)?(%s\\s)?\\s*%s\\s+(\\<%s\\>)\\s*(\\(|\\[|\\{)?%s(\\s+|\\)|\\]|\\}|\\|)\\s*%s$",
            REX_DECKSEC_XMAGE, REX_CARD_COUNT, REX_CARD_NAME, REX_COLL_NUMBER, REX_SET_CODE, REX_FOIL_MTGGOLDFISH);
    public static final Pattern CARD_COLLNO_SET_PATTERN = Pattern.compile(REX_FULL_REQUEST_CARD_COLLNO_SET);
    // 6. XMage format (Amount?, [Set:Collector Number] Card Name)
    public static final String REX_FULL_REQUEST_XMAGE = String.format(
            "^(%s\\s*:\\s*)?(%s\\s)?\\s*(\\[)?%s:%s(\\])\\s+%s\\s*%s$",
            REX_DECKSEC_XMAGE, REX_CARD_COUNT, REX_SET_CODE, REX_COLL_NUMBER, REX_CARD_NAME, REX_FOIL_MTGGOLDFISH);
    public static final Pattern SET_COLLNO_CARD_XMAGE_PATTERN = Pattern.compile(REX_FULL_REQUEST_XMAGE);
    // 7. Card-Only Request (Amount?)
    public static final String REX_CARDONLY = String.format(
            "(%s\\s*:\\s*)?(%s\\s)?\\s*%s\\s*%s", REX_DECKSEC_XMAGE, REX_CARD_COUNT, REX_CARD_NAME, REX_FOIL_MTGGOLDFISH);
    public static final Pattern CARD_ONLY_PATTERN = Pattern.compile(REX_CARDONLY);

    // CoreTypes (to recognise Tokens of type CardType
    private static final CharSequence[] CARD_TYPES = allCardTypes();
    private static final CharSequence[] DECK_SECTION_NAMES = {
            "side", "sideboard", "sb",
            "main", "card", "mainboard",
            "avatar", "commander", "schemes",
            "conspiracy", "planes", "deck", };

    private static CharSequence[] allCardTypes(){
        List<String> cardTypesList = new ArrayList<>();
        // CoreTypesNames
        List<CardType.CoreType> coreTypes = Lists.newArrayList(CardType.CoreType.values());
        for (CardType.CoreType coreType : coreTypes)
            cardTypesList.add(coreType.name().toLowerCase());
        // Manual Additions:
        // NOTE: "sorceries" is also included as it can be found in exported deck, even if it's incorrect.
        // Example: https://deckstats.net/decks/70852/556925-artifacts/en - see Issue 1010
        cardTypesList.add("sorceries");  // Sorcery is the only name with different plural form
        cardTypesList.add("aura");  // in case.
        cardTypesList.add("mana");  // "Mana" (see Issue 1010)
        cardTypesList.add("spell");
        cardTypesList.add("other spell");
        cardTypesList.add("planeswalker");
        return cardTypesList.toArray(new CharSequence[0]);
    }

    private Date releaseDateConstraint = null;
    // This two parameters are controlled only via setter methods
    private List<String> allowedSetCodes = null;  // as imposed by current format
    private DeckFormat deckFormat = null;  //

    public Token recognizeLine(final String rawLine, DeckSection referenceSection) {
        if (rawLine == null)
            return null;
        if (StringUtils.isBlank(rawLine.trim()))
            return null;

        final char smartQuote = (char) 8217;
        String refLine = rawLine.trim().replace(smartQuote, '\'');
        // Remove any link (e.g. Markdown Export format from TappedOut)
        refLine = purgeAllLinks(refLine);

        String line;
        if (StringUtils.startsWith(refLine, LINE_COMMENT_DELIMITER_OR_MD_HEADER))
            line = refLine.replaceAll(LINE_COMMENT_DELIMITER_OR_MD_HEADER, "");
        else
            line = refLine.trim();  // Remove any trailing formatting

        // Some websites export split card names with a single slash. Replace with double slash.
        line = SEARCH_SINGLE_SLASH.matcher(line).replaceFirst(" // ");
        if (StringUtils.startsWith(line, ASTERISK))  // markdown lists (tappedout md export)
            line = line.substring(2);

        Token result = recogniseCardToken(line, referenceSection);
        if (result == null)
            result = recogniseNonCardToken(line);
        return result != null ? result : StringUtils.startsWith(refLine, DOUBLE_SLASH) || StringUtils.startsWith(refLine, LINE_COMMENT_DELIMITER_OR_MD_HEADER) ?
                new Token(TokenType.COMMENT, 0, refLine) : new Token(TokenType.UNKNOWN_TEXT, 0, refLine);
    }

    public static String purgeAllLinks(String line){
        String urlPattern = "(?<protocol>((https|ftp|file|http):))(?<sep>((//|\\\\)+))(?<url>([\\w\\d:#@%/;$~_?+-=\\\\.&]*))";
        Pattern p = Pattern.compile(urlPattern, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(line);

        while (m.find()) {
            line = line.replaceAll(m.group(), "").trim();
        }
        if (StringUtils.endsWith(line, "()"))
            return line.substring(0, line.length()-2);
        return line;
    }

    public Token recogniseCardToken(final String text, final DeckSection referenceSection) {
        String line = text.trim();
        Token uknonwnCardToken = null;
        StaticData data = StaticData.instance();
        List<Matcher> cardMatchers = getRegExMatchers(line);
        for (Matcher matcher : cardMatchers) {
            String cardName = getRexGroup(matcher, REGRP_CARD);
            if (cardName == null)
                continue;
            cardName = cardName.trim();
            //Avoid hit the DB - check whether cardName is contained in the DB
            if (!data.isMTGCard(cardName))
                continue;  // skip to the next matcher!
            String ccount = getRexGroup(matcher, REGRP_CARDNO);
            String setCode = getRexGroup(matcher, REGRP_SET);
            String collNo = getRexGroup(matcher, REGRP_COLLNR);
            String foilGr = getRexGroup(matcher, REGRP_FOIL_GFISH);
            String deckSec = getRexGroup(matcher, REGRP_DECK_SEC_XMAGE_STYLE);
            boolean isFoil = foilGr != null;
            int cardCount = ccount != null ? Integer.parseInt(ccount) : 1;
            // if any, it will be tried to convert specific collector number to art index (useful for lands).
            String collectorNumber = collNo != null ? collNo : IPaperCard.NO_COLLECTOR_NUMBER;
            int artIndex;
            try {
                artIndex = Integer.parseInt(collectorNumber);
            } catch (NumberFormatException ex) {
                artIndex = IPaperCard.NO_ART_INDEX;
            }
            DeckSection tokenSection = getTokenSection(deckSec, referenceSection);
            if (setCode != null) {
                // Ok Now we're sure the cardName is correct. Now check for setCode
                CardEdition edition = StaticData.instance().getEditions().get(setCode);
                if (edition == null) {
                    // set the case for unknown card (in case) and continue to the next for any better matching
                    uknonwnCardToken = Token.UnknownCard(cardName, setCode, cardCount);
                    continue;
                }

                // we now name is ok, set is ok - we just need to be sure about collector number (if any)
                // and if that card can be actually found in the requested set.
                // IOW: we should account for wrong request, e.g. Counterspell|FEM - just doesn't exist!
                PaperCard pc = data.getCardFromSet(cardName, edition, collectorNumber, artIndex, isFoil);
                if (pc != null) {
                    // ok so the card has been found - let's see if there's any restriction on the set
                    if (isIllegalSetInGameFormat(edition.getCode()) || isIllegalCardInDeckFormat(pc))
                        // Mark as illegal card
                        return Token.IllegalCard(pc, cardCount);

                    if (isNotCompliantWithReleaseDateRestrictions(edition))
                        return Token.InvalidCard(pc, cardCount);

                    return Token.KnownCard(pc, cardCount, tokenSection);
                }
                // UNKNOWN card as in the Counterspell|FEM case
                return Token.UnknownCard(cardName, setCode, cardCount);
            }
            // ok so we can simply ignore everything but card name - as set code does not exist
            // At this stage, we know the card name exists in the DB so a Card MUST be found
            // unless it is illegal for current format or invalid with selected date.
            PaperCard pc = null;
            if (hasGameFormatConstraints()) {
                pc = data.getCardFromSupportedEditions(cardName, isFoil, this.allowedSetCodes,
                                                        this.releaseDateConstraint);
            }
            if (pc == null)
                pc = data.getCardFromSupportedEditions(cardName, isFoil, null,
                                                        this.releaseDateConstraint);

            if (pc != null) {
                if (isIllegalSetInGameFormat(pc.getEdition()) || isIllegalCardInDeckFormat(pc))
                    return Token.IllegalCard(pc, cardCount);
                CardEdition edition = StaticData.instance().getCardEdition(pc.getEdition());
                if (isNotCompliantWithReleaseDateRestrictions(edition))
                    return Token.InvalidCard(pc, cardCount);
                return Token.KnownCard(pc, cardCount, tokenSection);
            }
        }
        return uknonwnCardToken;  // either null or unknown card
    }

    private DeckSection getTokenSection(String deckSec, DeckSection currentDeckSection){
        if (deckSec == null)
            return currentDeckSection == null ? DeckSection.Main : currentDeckSection;
        switch (deckSec.toUpperCase().trim()){
            case "MB":
                return DeckSection.Main;
            case "SB":
                return DeckSection.Sideboard;
            case "CM":
                return DeckSection.Commander;
            default:
                return DeckSection.Main;
        }
    }

    private boolean hasGameFormatConstraints() {
        return this.allowedSetCodes != null && this.allowedSetCodes.size() > 0;
    }

    private String getRexGroup(Matcher matcher, String groupName){
        String rexGroup;
        try{
            rexGroup = matcher.group(groupName);
        } catch (IllegalArgumentException ex) {
            rexGroup = null;
        }
        return rexGroup;
    }

    private boolean isIllegalCardInDeckFormat(PaperCard pc) {
        return this.deckFormat != null && !this.deckFormat.isLegalCard(pc);
    }

    private boolean isIllegalSetInGameFormat(String setCode) {
        return this.allowedSetCodes != null && !this.allowedSetCodes.contains(setCode);
    }

    private boolean isNotCompliantWithReleaseDateRestrictions(CardEdition edition){
        return this.releaseDateConstraint != null && edition.getDate().compareTo(this.releaseDateConstraint) >= 0;
    }

    private List<Matcher> getRegExMatchers(String line) {
        List<Matcher> matchers = new ArrayList<>();
        Pattern[] patternsWithCollNumber = new Pattern[] {
                CARD_SET_COLLNO_PATTERN,
                SET_CARD_COLLNO_PATTERN,
                CARD_COLLNO_SET_PATTERN,
                SET_COLLNO_CARD_XMAGE_PATTERN
        };
        for (Pattern pattern : patternsWithCollNumber) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches() && getRexGroup(matcher, REGRP_SET) != null &&
                    getRexGroup(matcher, REGRP_COLLNR) != null)
                matchers.add(matcher);
        }
        Pattern[] OtherPatterns = new Pattern[] {  // Order counts
                CARD_SET_PATTERN,
                SET_CARD_PATTERN,
                CARD_ONLY_PATTERN
        };
        for (Pattern pattern : OtherPatterns) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches())
                matchers.add(matcher);
        }
        return matchers;
    }

    public Token recogniseNonCardToken(final String text) {
        if (isDeckSectionName(text)) {
            String tokenText = nonCardTokenMatch(text);
            return Token.DeckSection(tokenText);
        }
        if (isCardCMC(text)){
            String tokenText = cardCMCTokenMatch(text);
            return new Token(TokenType.CARD_CMC, tokenText);
        }
        if (isCardRarity(text)){
            String tokenText = cardRarityTokenMatch(text);
            if (tokenText != null && !tokenText.trim().equals(""))
                return new Token(TokenType.CARD_RARITY, tokenText);
            return null;
        }
        if (isCardType(text)){
            String tokenText = nonCardTokenMatch(text);
            return new Token(TokenType.CARD_TYPE, tokenText);
        }
        if(isManaToken(text)){
            String tokenText = manaTokenMatch(text);
            return new Token(TokenType.MANA_COLOUR, tokenText);
        }
        if (isDeckName(text)) {
            String deckName = deckNameMatch(text);
            return new Token(TokenType.DECK_NAME, deckName);
        }
        return null;
    }

    /* -----------------------------------------------------------------------------
    Note: Card types, CMC, and Rarity Tokens are **only** used for style formatting
    in the Import Editor. This won't affect the import process in any way.
    The use of this token has been borrowed by Deckstats.net format export.
    ----------------------------------------------------------------------------- */
    public static boolean isCardType(final String lineAsIs) {
        String nonCardToken = nonCardTokenMatch(lineAsIs);
        if (nonCardToken == null)
            return false;
        return StringUtils.containsAny(nonCardToken.toLowerCase(), CARD_TYPES);
    }

    public static boolean isCardRarity(final String lineAsIs){
        return cardRarityTokenMatch(lineAsIs) != null;
    }

    public static boolean isCardCMC(final String lineAsIs) {
        return cardCMCTokenMatch(lineAsIs) != null;
    }

    public static boolean isManaToken(final String lineAsIs) {
        return manaTokenMatch(lineAsIs) != null;
    }

    public static boolean isDeckSectionName(final String lineAsIs) {
        String nonCardToken = nonCardTokenMatch(lineAsIs);
        if (nonCardToken == null)
            return false;
        return StringUtils.equalsAnyIgnoreCase(nonCardToken, DECK_SECTION_NAMES);
    }

    private static String nonCardTokenMatch(final String lineAsIs){
        if (lineAsIs == null)
            return null;
        String line = lineAsIs.trim();
        Matcher noncardMatcher = NONCARD_PATTERN.matcher(line);
        if (!noncardMatcher.matches())
            return null;
        return noncardMatcher.group(REGRP_TOKEN);
    }

    private static String cardRarityTokenMatch(final String lineAsIs){
        if (lineAsIs == null)
            return null;
        String line = lineAsIs.trim();
        Matcher cardRarityMatcher = CARD_RARITY_PATTERN.matcher(line);
        if (!cardRarityMatcher.matches())
            return null;
        return cardRarityMatcher.group(REGRP_TOKEN);
    }

    private static String cardCMCTokenMatch(final String lineAsIs){
        if (lineAsIs == null)
            return null;
        String line = lineAsIs.trim();
        Matcher cardCMCmatcher = CMC_PATTERN.matcher(line);
        if (!cardCMCmatcher.matches())
            return null;
        return cardCMCmatcher.group(REGRP_TOKEN);
    }

    private static String manaTokenMatch(final String lineAsIs){
        if (lineAsIs == null)
            return null;
        String line = lineAsIs.trim();
        Matcher manaMatcher = MANA_PATTERN.matcher(line);
        if (!manaMatcher.matches())
            return null;
        return manaMatcher.group(REGRP_TOKEN);
    }

    public static boolean isDeckName(final String lineAsIs) {
        if (lineAsIs == null)
            return false;
        final String line = lineAsIs.trim();
        final Matcher deckNameMatcher = DECK_NAME_PATTERN.matcher(line);
        return deckNameMatcher.matches();
    }

    public static String deckNameMatch(final String text) {
        if (text == null)
            return "";
        String line = text.trim();
        final Matcher deckNamePattern = DECK_NAME_PATTERN.matcher(line);
        if (deckNamePattern.matches())
            return deckNamePattern.group(REGRP_DECKNAME);  // Deck name is at match 7
        return "";
    }

    public void setDateConstraint(int year, int month) {
        Calendar ca = Calendar.getInstance();
        ca.set(year, month, 1);
        releaseDateConstraint = ca.getTime();
    }

    public void setGameFormatConstraint(List<String> allowedSetCodes){
        this.allowedSetCodes = allowedSetCodes;
    }

    public void setDeckFormatConstraint(DeckFormat deckFormat0){
        this.deckFormat = deckFormat0;
    }
}
