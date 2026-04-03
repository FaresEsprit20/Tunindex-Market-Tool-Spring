package com.tunindex.market_tool.core.utils.constants;

import com.tunindex.market_tool.domain.entities.enums.OwnershipType;

import java.util.HashMap;
import java.util.Map;

public interface Constants {

    String APP_ROOT = "tunindex/market/tool/v1";
    String ALLOWED_ORIGINS = "http://localhost:4200";
    Boolean PRODUCTION_ENVIRONMENT = false;

    // ========================
    // WEB CLIENT CONSTANTS
    // ========================
    String USER_AGENT_HEADER = "User-Agent";
    String ACCEPT_HEADER = "Accept";
    String ACCEPT_LANGUAGE_HEADER = "Accept-Language";
    String ACCEPT_ENCODING_HEADER = "Accept-Encoding";
    String CONNECTION_HEADER = "Connection";


    // Add these to your Constants interface
    String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    String DEFAULT_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7";
    String DEFAULT_ACCEPT_LANGUAGE = "en-US,en;q=0.9";
    String DEFAULT_ACCEPT_ENCODING = "gzip, deflate, br";
    String DEFAULT_CONNECTION = "keep-alive";
    // ========================
    // SCRAPING CONSTANTS
    // ========================
    int REQUEST_TIMEOUT_MS = 10000;
    int CONNECT_TIMEOUT_MS = 10000;
    int READ_TIMEOUT_MS = 30000;

    int MAX_RETRY_ATTEMPTS = 3;
    long RETRY_DELAY_MS = 1000;
    long RETRY_MAX_DELAY_MS = 10000;

    double RATE_LIMIT_DELAY_SECONDS = 1.5;
    int RATE_LIMIT_PER_MINUTE = 40;

    // ========================
    // PARALLELISM
    // ========================
    int DEFAULT_MAX_WORKERS = 10;
    boolean BVPS_SKIP_IF_EXISTS = true;

    // ========================
    // PROVIDER CONSTANTS
    // ========================
    String ACTIVE_PROVIDER = "investingcom";

    String PROVIDER_INVESTINGCOM = "investingcom";
    String PROVIDER_ILBOURSA = "ilboursa";
    String PROVIDER_BVMT = "bvmt";
    String PROVIDER_TUNISIE_VALEURS = "tunisie_valeurs";

    String INVESTINGCOM_BASE_URL = "https://www.investing.com";
    String INVESTINGCOM_FINANCIAL_SUMMARY = "-financial-summary";
    String INVESTINGCOM_BALANCE_SHEET = "-balance-sheet";
    String INVESTINGCOM_INCOME_STATEMENT = "-income-statement";

    // ========================
    // CACHE
    // ========================
    int CACHE_TTL_SECONDS = 3600;

    // ========================
    // PROXY SETTINGS
    // ========================
    boolean USE_PROXY = false;
    // ========================
    // TUNISIAN STOCKS
    // ========================
    Map<String, StockInfo> TUNISIAN_STOCKS = new HashMap<>() {{
        // Government-owned
        put("STB", new StockInfo("STB", "S.T.B", "/equities/societe-tunisienne-de-banque", OwnershipType.GOVERNMENT));
        put("BH", new StockInfo("BH", "BH Bank", "/equities/banque-de-lhabitat", OwnershipType.GOVERNMENT));
        put("BNA", new StockInfo("BNA", "BNA", "/equities/banque-nationale-agricole", OwnershipType.GOVERNMENT));

        // Mixed ownership
        put("BIAT", new StockInfo("BIAT", "BIAT", "/equities/banque-inter.-arabe-de-tunisie", OwnershipType.PRIVATE));
        put("UIB", new StockInfo("UIB", "UIB", "/equities/union-internationale-de-banque", OwnershipType.PRIVATE));
        put("ATB", new StockInfo("ATB", "ATB", "/equities/arab-tunisian-bank", OwnershipType.PRIVATE));
        put("UBCI", new StockInfo("UBCI", "Union Bancaire pour le Commerce et l'Industrie", "/equities/u.b.c.i", OwnershipType.PRIVATE));

        // Private sector
        put("AB", new StockInfo("AB", "AMEN BANK", "/equities/amen-bank", OwnershipType.PRIVATE));
        put("AL", new StockInfo("AL", "AIR LIQUIDE Tun", "/equities/air-liquide-tunisie", OwnershipType.PRIVATE));
        put("ARTES", new StockInfo("ARTES", "Automobile Reseau Tunisien Et Service", "/equities/artes-renault", OwnershipType.PRIVATE));
        put("AST", new StockInfo("AST", "ASTREE SA", "/equities/com.-dassur.et-de-reassur.", OwnershipType.PRIVATE));
        put("ATL", new StockInfo("ATL", "ATL", "/equities/arab-tunisian-lease", OwnershipType.PRIVATE));
        put("BS", new StockInfo("BS", "ATTIJARI BANK", "/equities/banque-attijari-de-tunisie", OwnershipType.PRIVATE));
        put("BT", new StockInfo("BT", "BT", "/equities/banque-de-tunisie", OwnershipType.PRIVATE));
        put("BTEI", new StockInfo("BTEI", "BTEI", "/equities/bq-de-tunisie-et-des-emirats", OwnershipType.PRIVATE));
        put("CC", new StockInfo("CC", "Carthage Cement", "/equities/carthage-cement", OwnershipType.PRIVATE));
        put("CIL", new StockInfo("CIL", "CIL", "/equities/compagnie-int.-de-leasing", OwnershipType.PRIVATE));
        put("ICF", new StockInfo("ICF", "ICF", "/equities/soc.-des-ind.-chimiu.-du-fluor", OwnershipType.PRIVATE));
        put("MGR", new StockInfo("MGR", "Societe Tunisienne des Marches de Gros", "/equities/sotumag", OwnershipType.PRIVATE));
        put("BHL", new StockInfo("BHL", "BH Leasing", "/equities/modern-leasing", OwnershipType.PRIVATE));
        put("MNP", new StockInfo("MNP", "Societe Nouvelle Maison de la Ville de Tunis", "/equities/monoprix", OwnershipType.PRIVATE));
        put("NAKL", new StockInfo("NAKL", "Ennakl Automobiles", "/equities/ennakl-automobiles", OwnershipType.PRIVATE));
        put("PLTU", new StockInfo("PLTU", "PLACEMENT DE TUNISIE", "/equities/placements-de-tunisie", OwnershipType.PRIVATE));
        put("POULA", new StockInfo("POULA", "POULINA GROUP HLD", "/equities/poulina-group-holding", OwnershipType.PRIVATE));
        put("SCB", new StockInfo("SCB", "Les Ciments de Bizerte", "/equities/ciments-de-bizerte", OwnershipType.PRIVATE));
        put("SFBT", new StockInfo("SFBT", "SFBT", "/equities/sfbt", OwnershipType.PRIVATE));
        put("SIAM", new StockInfo("SIAM", "STE Ind d'appareillage Et De Materiels Elec", "/equities/siame", OwnershipType.PRIVATE));
        put("SIMP", new StockInfo("SIMP", "SIMPAR", "/equities/soc.-immob.-et-de-part.", OwnershipType.PRIVATE));
        put("SITS", new StockInfo("SITS", "SITS", "/equities/soc.-immob.-tuniso-seoud.", OwnershipType.PRIVATE));
        put("SMG", new StockInfo("SMG", "MAGASIN GENERAL", "/equities/magazin-gneral", OwnershipType.PRIVATE));
        put("SOKNA", new StockInfo("SOKNA", "ESSOUKNA", "/equities/societe-essoukna", OwnershipType.PRIVATE));
        put("SOMOC", new StockInfo("SOMOC", "SOMOCER", "/equities/societe-moderne-de-ceramique", OwnershipType.PRIVATE));
        put("SOTE", new StockInfo("SOTE", "STE Tunisienne d'entreprises De Telecommunications", "/equities/sotetel", OwnershipType.PRIVATE));
        put("SPDI", new StockInfo("SPDI", "SPDIT-SICAF", "/equities/spdit", OwnershipType.PRIVATE));
        put("STAR", new StockInfo("STAR", "STAR", "/equities/star", OwnershipType.PRIVATE));
        put("STIP", new StockInfo("STIP", "Societe Tunisienne des Industries de Pneumatiques", "/equities/soc.-tun.-des-ind.-de-pneumatiques", OwnershipType.PRIVATE));
        put("STPIL", new StockInfo("STPIL", "SOTRAPIL", "/equities/sotrapil", OwnershipType.PRIVATE));
        put("TINV", new StockInfo("TINV", "TUN INVEST - SICAR", "/equities/tuninvest", OwnershipType.PRIVATE));
        put("TJL", new StockInfo("TJL", "ATTIJARI LEASING", "/equities/attijari-leasing", OwnershipType.PRIVATE));
        put("TLNET", new StockInfo("TLNET", "TELNET", "/equities/telnet-holding", OwnershipType.PRIVATE));
        put("TLS", new StockInfo("TLS", "TUNISIE LEASING", "/equities/tunisie-leasing", OwnershipType.PRIVATE));
        put("TPR", new StockInfo("TPR", "TPR", "/equities/soc.-tun.-profiles-aluminium", OwnershipType.PRIVATE));
        put("TRE", new StockInfo("TRE", "Tunis Re", "/equities/soc.-tun.-de-reassurance", OwnershipType.PRIVATE));
        put("WIFAK", new StockInfo("WIFAK", "EL WIFACK LEASING", "/equities/el-wifack-leasing", OwnershipType.PRIVATE));
        put("STVR", new StockInfo("STVR", "Societe Tunisienne De Verreries", "/equities/soc-tunisienne-de-verreries", OwnershipType.PRIVATE));
        put("BHASS", new StockInfo("BHASS", "BH Assurance", "/equities/salim", OwnershipType.PRIVATE));
        put("LNDOR", new StockInfo("LNDOR", "Land Or", "/equities/land-or", OwnershipType.PRIVATE));
        put("NBL", new StockInfo("NBL", "New Body Li", "/equities/new-body-li", OwnershipType.PRIVATE));
        put("OTH", new StockInfo("OTH", "One Tech Ho", "/equities/one-tech-ho", OwnershipType.PRIVATE));
        put("STPAP", new StockInfo("STPAP", "Societe Tunisienne Industrielle Du Papier Et Du Ca", "/equities/sotipapier", OwnershipType.PRIVATE));
        put("SOTEM", new StockInfo("SOTEM", "Sotemail", "/equities/sotemail", OwnershipType.PRIVATE));
        put("SAH", new StockInfo("SAH", "Sah", "/equities/sah", OwnershipType.PRIVATE));
        put("HANL", new StockInfo("HANL", "Hannibal Lease", "/equities/hannibal-lease", OwnershipType.PRIVATE));
        put("CITY", new StockInfo("CITY", "City Cars", "/equities/city-cars", OwnershipType.PRIVATE));
        put("ECYCL", new StockInfo("ECYCL", "Euro-Cycles", "/equities/euro-cycles", OwnershipType.PRIVATE));
        put("MPBS", new StockInfo("MPBS", "Manufacture de Panneaux Bois du Sud", "/equities/mpbs", OwnershipType.PRIVATE));
        put("BL", new StockInfo("BL", "Best Lease", "/equities/best-lease", OwnershipType.PRIVATE));
        put("DH", new StockInfo("DH", "Societe Delice Holding", "/equities/societe-delice-holding", OwnershipType.PRIVATE));
        put("PLAST", new StockInfo("PLAST", "OfficePlast", "/equities/officeplast", OwnershipType.PRIVATE));
        put("UMED", new StockInfo("UMED", "Unite de Fabrication de Medicaments", "/equities/unimed-sa", OwnershipType.PRIVATE));
        put("SAMAA", new StockInfo("SAMAA", "Atelier Meuble Interieurs", "/equities/atelier-meuble-interieurs", OwnershipType.PRIVATE));
        put("ASSMA", new StockInfo("ASSMA", "Ste Assurances Magrebia", "/equities/ste-assurances-magrebia", OwnershipType.PRIVATE));
        put("SMART", new StockInfo("SMART", "Smart Tunisie", "/equities/smart-tunisie", OwnershipType.PRIVATE));
        put("STAS", new StockInfo("STAS", "Societe Tunisienne D Automobiles", "/equities/societe-tunisienne-d-automobiles", OwnershipType.PRIVATE));
        put("AMV", new StockInfo("AMV", "Assurances Maghrebia Vie", "/equities/assurances-maghrebia-vie", OwnershipType.PRIVATE));
    }};

    @lombok.Data
    @lombok.AllArgsConstructor
    class StockInfo {
        String symbol;
        String name;
        String url;
        OwnershipType ownershipType;

        public StockInfo(String symbol, String name, String url) {
            this(symbol, name, url, OwnershipType.PRIVATE);
        }
    }
}