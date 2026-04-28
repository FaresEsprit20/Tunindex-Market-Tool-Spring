package com.tunindex.market_tool.core.utils.constants;

import com.tunindex.market_tool.domain.entities.enums.OwnershipType;

import java.util.LinkedHashMap;
import java.util.Map;

public interface Constants {

    String APP_ROOT = "tunindex/market/tool/v1";
    String ALLOWED_ORIGINS = "http://localhost:4200";
    Boolean PRODUCTION_ENVIRONMENT = false;

    // ========================
    // FLARESOLVERR CONFIGURATION
    // ========================
    String FLARESOLVERR_URL = "http://localhost:8191";
    int FLARESOLVERR_TIMEOUT = 1200000;
    int FLARESOLVERR_MAX_RETRIES = 2;

    // ========================
    // WEB CLIENT CONSTANTS
    // ========================
    String USER_AGENT_HEADER = "User-Agent";
    String ACCEPT_HEADER = "Accept";
    String ACCEPT_LANGUAGE_HEADER = "Accept-Language";
    String ACCEPT_ENCODING_HEADER = "Accept-Encoding";
    String CONNECTION_HEADER = "Connection";

    String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
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

    String PROVIDER_STOCKANALYSIS = "stockanalysis";

    String INVESTINGCOM_BASE_URL = "https://www.investing.com";
    String INVESTINGCOM_FINANCIAL_SUMMARY = "-financial-summary";
    String INVESTINGCOM_BALANCE_SHEET = "-balance-sheet";
    String INVESTINGCOM_INCOME_STATEMENT = "-income-statement";

    // StockAnalysis URLs
    String STOCKANALYSIS_BASE_URL = "https://stockanalysis.com/quote/bvmt/";
    String STOCKANALYSIS_LIST_URL = "https://stockanalysis.com/list/tunis-stock-exchange/";

    // ========================
    // CACHE
    // ========================
    int CACHE_TTL_SECONDS = 3600;

    // ========================
    // PROXY SETTINGS
    // ========================
    boolean USE_PROXY = false;

    // ========================
    // TUNISIAN STOCKS (73 stocks from StockAnalysis with Industry and Country)
    // ========================
    Map<String, StockInfo> TUNISIAN_STOCKS_STOCK_ANALYSIS = new LinkedHashMap<>() {{
        // 1
        put("BIAT", new StockInfo("BIAT", "Banque Internationale Arabe de Tunisie Société anonyme", "/quote/bvmt/BIAT/", OwnershipType.PRIVATE, "Commercial Banks", "Tunisia"));
        // 2
        put("PGH", new StockInfo("PGH", "Poulina Group Holding S.A.", "/quote/bvmt/PGH/", OwnershipType.PRIVATE, "Poultry Slaughtering and Processing", "Tunisia"));
        // 3
        put("SFBT", new StockInfo("SFBT", "Société de Fabrication des Boissons de Tunisie Société Anonyme", "/quote/bvmt/SFBT/", OwnershipType.PRIVATE, "Beverages", "Tunisia"));
        // 4
        put("TJARI", new StockInfo("TJARI", "Banque Attijari de Tunisie Société anonyme", "/quote/bvmt/TJARI/", OwnershipType.PRIVATE, "Commercial Banks", "Tunisia"));
        // 5
        put("AB", new StockInfo("AB", "Amen Bank Société anonyme", "/quote/bvmt/AB/", OwnershipType.PRIVATE, "Commercial Banks", "Tunisia"));
        // 6
        put("DH", new StockInfo("DH", "Délice Holding SA", "/quote/bvmt/DH/", OwnershipType.PRIVATE, "Dairy Products", "Tunisia"));
        // 7
        put("BT", new StockInfo("BT", "Banque de Tunisie Société anonyme", "/quote/bvmt/BT/", OwnershipType.PRIVATE, "Commercial Banks", "Tunisia"));
        // 8
        put("SAH", new StockInfo("SAH", "Société d'Articles Hygiéniques Société Anonyme", "/quote/bvmt/SAH/", OwnershipType.PRIVATE, "Orthopedic, Prosthetic, and Surgical Appliances and Supplies", "Tunisia"));
        // 9
        put("BNA", new StockInfo("BNA", "Banque Nationale Agricole Société anonyme", "/quote/bvmt/BNA/", OwnershipType.PRIVATE, "Commercial Banks", "Tunisia"));
        // 10
        put("UIB", new StockInfo("UIB", "Union Internationale de Banques Société anonyme", "/quote/bvmt/UIB/", OwnershipType.PRIVATE, "Commercial Banks", "Tunisia"));
        // 11
        put("OTH", new StockInfo("OTH", "One Tech Holding S.A.", "/quote/bvmt/OTH/", OwnershipType.PRIVATE, "Drawing and Insulating of Nonferrous Wire", "Tunisia"));
        // 12
        put("SOTUV", new StockInfo("SOTUV", "Societe Tunisienne de Verreries", "/quote/bvmt/SOTUV/", OwnershipType.PRIVATE, "Glass And Glassware, Pressed Or Blown", "Tunisia"));
        // 13
        put("UBCI", new StockInfo("UBCI", "Union Bancaire pour le Commerce et L'Industrie Société anonyme", "/quote/bvmt/UBCI/", OwnershipType.PRIVATE, "Commercial Banks", "Tunisia"));
        // 14
        put("STB", new StockInfo("STB", "Société Tunisienne de Banque Société anonyme", "/quote/bvmt/STB/", OwnershipType.GOVERNMENT, "Commercial Banks", "Tunisia"));
        // 15
        put("CC", new StockInfo("CC", "Carthage Cement SA", "/quote/bvmt/CC/", OwnershipType.PRIVATE, "Cement, Hydraulic", "Tunisia"));
        // 16
        put("STAR", new StockInfo("STAR", "Société Tunisienne d'Assurances et de Réassurances", "/quote/bvmt/STAR/", OwnershipType.PRIVATE, "Insurance Carriers", "Tunisia"));
        // 17
        put("TPR", new StockInfo("TPR", "Tunisie Profilés Aluminium Société Anonyme", "/quote/bvmt/TPR/", OwnershipType.PRIVATE, "Metal Doors, Sash, Frames, Molding, and Trim Manufacturing", "Tunisia"));
        // 18
        put("NAKL", new StockInfo("NAKL", "Ennakl Automobiles S.A.", "/quote/bvmt/NAKL/", OwnershipType.PRIVATE, "Automotive Dealers and Gasoline Service Stations", "Tunisia"));
        // 19
        put("BH", new StockInfo("BH", "BH Bank Société anonyme", "/quote/bvmt/BH/", OwnershipType.GOVERNMENT, "Mortgage Bankers and Loan Correspondents", "Tunisia"));
        // 20
        put("ATB", new StockInfo("ATB", "Arab Tunisian Bank", "/quote/bvmt/ATB/", OwnershipType.PRIVATE, "Commercial Banks", "Tunisia"));
        // 21
        put("ARTES", new StockInfo("ARTES", "Automobile Réseau Tunisien et Services S.A.", "/quote/bvmt/ARTES/", OwnershipType.PRIVATE, "Automotive Dealers and Gasoline Service Stations", "Tunisia"));
        // 22
        put("SPDIT", new StockInfo("SPDIT", "Société de Placement & de Développement Industriel & Touristique Société anonyme", "/quote/bvmt/SPDIT/", OwnershipType.PRIVATE, "Unit Investment Trusts, Face-Amount Certificate Offices, and Closed-End Management Investment Offices", "Tunisia"));
        // 23
        put("TLS", new StockInfo("TLS", "Tunisie Leasing & Factoring Société anonyme", "/quote/bvmt/TLS/", OwnershipType.PRIVATE, "Miscellaneous business Credit Institutions", "Tunisia"));
        // 24
        put("CITY", new StockInfo("CITY", "City Cars S.A.", "/quote/bvmt/CITY/", OwnershipType.PRIVATE, "Automotive Dealers and Gasoline Service Stations", "Tunisia"));
        // 25
        put("AST", new StockInfo("AST", "Compagnie d'Assurances et de Réassurances ASTREE", "/quote/bvmt/AST/", OwnershipType.PRIVATE, "Insurance Carriers", "Tunisia"));
        // 26
        put("ASSMA", new StockInfo("ASSMA", "Assurances Maghrebia SA", "/quote/bvmt/ASSMA/", OwnershipType.PRIVATE, "Insurance Carriers, not elsewhere classified", "Tunisia"));
        // 27
        put("BNASS", new StockInfo("BNASS", "BNA Assurances", "/quote/bvmt/BNASS/", OwnershipType.PRIVATE, "Fire, Marine, and Casualty Insurance", "Tunisia"));
        // 28
        put("AL", new StockInfo("AL", "Air Liquide Tunisie SA", "/quote/bvmt/AL/", OwnershipType.PRIVATE, "Industrial Inorganic Chemicals", "Tunisia"));
        // 29
        put("UMED", new StockInfo("UMED", "Unité de Fabrication des Médicaments S.A", "/quote/bvmt/UMED/", OwnershipType.PRIVATE, "Pharmaceutical Preparations", "Tunisia"));
        // 30
        put("ATL", new StockInfo("ATL", "Arab Tunisian Lease S.A.", "/quote/bvmt/ATL/", OwnershipType.PRIVATE, "Miscellaneous business Credit Institutions", "Tunisia"));
        // 31
        put("SMART", new StockInfo("SMART", "SMART Tunisie SA", "/quote/bvmt/SMART/", OwnershipType.PRIVATE, "Computers and Computer Peripheral Equipment and Software", "Tunisia"));
        // 32
        put("TRE", new StockInfo("TRE", "Société Tunisienne de Réassurance", "/quote/bvmt/TRE/", OwnershipType.PRIVATE, "Insurance Carriers", "Tunisia"));
        // 33
        put("WIFAK", new StockInfo("WIFAK", "Wifak International Bank", "/quote/bvmt/WIFAK/", OwnershipType.PRIVATE, "Commercial Banks", "Tunisia"));
        // 34
        put("MNP", new StockInfo("MNP", "Société Nouvelle Maison de la Ville de Tunis", "/quote/bvmt/MNP/", OwnershipType.PRIVATE, "Department Stores", "Tunisia"));
        // 35
        put("CIL", new StockInfo("CIL", "Compagnie Internationale de Leasing S.A.", "/quote/bvmt/CIL/", OwnershipType.PRIVATE, "Miscellaneous business Credit Institutions", "Tunisia"));
        // 36
        put("AMV", new StockInfo("AMV", "Assurances Maghrebia Vie S.A.", "/quote/bvmt/AMV/", OwnershipType.PRIVATE, "Insurance Carriers", "Tunisia"));
        // 37
        put("LNDOR", new StockInfo("LNDOR", "Land'Or Société Anonyme", "/quote/bvmt/LNDOR/", OwnershipType.PRIVATE, "Dairy Products", "Tunisia"));
        // 38
        put("MAG", new StockInfo("MAG", "Societe Magasin General S.A.", "/quote/bvmt/MAG/", OwnershipType.PRIVATE, "Miscellaneous General Merchandise Stores", "Tunisia"));
        // 39
        put("MPBS", new StockInfo("MPBS", "Manufacture De Panneaux Bois Du Sud", "/quote/bvmt/MPBS/", OwnershipType.PRIVATE, "Lumber and Wood Products, except Furniture", "Tunisia"));
        // 40
        put("ICF", new StockInfo("ICF", "Les industries Chimiques du Fluor SA", "/quote/bvmt/ICF/", OwnershipType.PRIVATE, "Industrial Inorganic Chemicals", "Tunisia"));
        // 41
        put("BHASS", new StockInfo("BHASS", "BH Assurance", "/quote/bvmt/BHASS/", OwnershipType.PRIVATE, "Insurance Carriers", "Tunisia"));
        // 42
        put("MGR", new StockInfo("MGR", "Société Tunisienne des Marchés de Gros S.A.", "/quote/bvmt/MGR/", OwnershipType.PRIVATE, "Groceries And Related Products", "Tunisia"));
        // 43
        put("TLNET", new StockInfo("TLNET", "Telnet Holding SA", "/quote/bvmt/TLNET/", OwnershipType.PRIVATE, "Computer Programming, Data Processing, And Other Computer Related Services", "Tunisia"));
        // 44
        put("STA", new StockInfo("STA", "Société Tunisienne d'Automobiles - STA Société anonyme", "/quote/bvmt/STA/", OwnershipType.PRIVATE, "Automotive Dealers and Gasoline Service Stations", "Tunisia"));
        // 45
        put("ECYCL", new StockInfo("ECYCL", "Euro-Cycles S.A", "/quote/bvmt/ECYCL/", OwnershipType.PRIVATE, "Motorcycles, Bicycles, and Parts", "Tunisia"));
        // 46
        put("STPIL", new StockInfo("STPIL", "La Société de Transport des Hydrocarbures par Pipelines SOTRAPIL SA", "/quote/bvmt/STPIL/", OwnershipType.PRIVATE, "Pipelines, Except Natural Gas", "Tunisia"));
        // 47
        put("SOTEM", new StockInfo("SOTEM", "Société Tunisienne d'Email S.A", "/quote/bvmt/SOTEM/", OwnershipType.PRIVATE, "Structural Clay Products", "Tunisia"));
        // 48
        put("TJL", new StockInfo("TJL", "Attijari Leasing S.A.", "/quote/bvmt/TJL/", OwnershipType.PRIVATE, "Miscellaneous business Credit Institutions", "Tunisia"));
        // 49
        put("TGH", new StockInfo("TGH", "Tawasol Group Holding SA", "/quote/bvmt/TGH/", OwnershipType.PRIVATE, "Water, Sewer, Pipeline, and Communications and Power Line Construction", "Tunisia"));
        // 50
        put("STPAP", new StockInfo("STPAP", "Société Tunisienne Industrielle du Papier et du Carton", "/quote/bvmt/STPAP/", OwnershipType.PRIVATE, "Paperboard Mills", "Tunisia"));
        // 51
        put("HL", new StockInfo("HL", "Hannibal Lease SA", "/quote/bvmt/HL/", OwnershipType.PRIVATE, "Miscellaneous business Credit Institutions", "Tunisia"));
        // 52
        put("BL", new StockInfo("BL", "Best Lease SA", "/quote/bvmt/BL/", OwnershipType.PRIVATE, "Miscellaneous business Credit Institutions", "Tunisia"));
        // 53
        put("ASSAD", new StockInfo("ASSAD", "L'Accumulateur Tunisien Assad SA", "/quote/bvmt/ASSAD/", OwnershipType.PRIVATE, "Miscellaneous Electrical Machinery, Equipment, and Supplies", "Tunisia"));
        // 54
        put("SIAME", new StockInfo("SIAME", "Société Industrielle d'Appareillage et de Matériels Electriques", "/quote/bvmt/SIAME/", OwnershipType.PRIVATE, "Electronic and Other Electrical Equipment and Components, except Computer Equipment", "Tunisia"));
        // 55
        put("ALKIM", new StockInfo("ALKIM", "Société Chimique ALKIMIA S.A.", "/quote/bvmt/ALKIM/", OwnershipType.PRIVATE, "Miscellaneous Chemical Products", "Tunisia"));
        // 56
        put("SITS", new StockInfo("SITS", "Société Immobilière Tunisio Saoudienne", "/quote/bvmt/SITS/", OwnershipType.PRIVATE, "Real Estate", "Tunisia"));
        // 57
        put("STIP", new StockInfo("STIP", "Société Tunisienne des Industries de Pneumatiques SA", "/quote/bvmt/STIP/", OwnershipType.PRIVATE, "Tires and Inner Tubes", "Tunisia"));
        // 58
        put("SOTET", new StockInfo("SOTET", "Société Tunisienne d'Entreprises de Télécommunications S.A.", "/quote/bvmt/SOTET/", OwnershipType.PRIVATE, "Communications Services, Not Elsewhere Classified", "Tunisia"));
        // 59
        put("SIMPAR", new StockInfo("SIMPAR", "Société Immobilière et de participations Société Anonyme", "/quote/bvmt/SIMPAR/", OwnershipType.PRIVATE, "Real Estate", "Tunisia"));
        // 60
        put("TAIR", new StockInfo("TAIR", "Société Tunisienne de l'Air S.A.", "/quote/bvmt/TAIR/", OwnershipType.PRIVATE, "Air Transportation, Scheduled", "Tunisia"));
        // 61
        put("SCB", new StockInfo("SCB", "Les Ciments de Bizerte", "/quote/bvmt/SCB/", OwnershipType.PRIVATE, "Cement, Hydraulic", "Tunisia"));
        // 62
        put("SOMOC", new StockInfo("SOMOC", "Société Moderne de Céramique", "/quote/bvmt/SOMOC/", OwnershipType.PRIVATE, "Structural Clay Products", "Tunisia"));
        // 63
        put("SAM", new StockInfo("SAM", "Société Atelier du Meuble Intérieurs SA", "/quote/bvmt/SAM/", OwnershipType.PRIVATE, "Office Furniture", "Tunisia"));
        // 64
        put("PLAST", new StockInfo("PLAST", "Office Plast SA", "/quote/bvmt/PLAST/", OwnershipType.PRIVATE, "Miscellaneous Manufacturing Industries", "Tunisia"));
        // 65
        put("BHL", new StockInfo("BHL", "BH Leasing Société Anonyme", "/quote/bvmt/BHL/", OwnershipType.PRIVATE, "Miscellaneous business Credit Institutions", "Tunisia"));
        // 66
        put("SOKNA", new StockInfo("SOKNA", "Essoukna", "/quote/bvmt/SOKNA/", OwnershipType.PRIVATE, "Land Subdividers and Developers, Except Cemeteries", "Tunisia"));
        // 67
        put("NBL", new StockInfo("NBL", "New Body Line Société Anonyme", "/quote/bvmt/NBL/", OwnershipType.PRIVATE, "Apparel and Other Finished Products Made From Fabrics and Similar Materials", "Tunisia"));
        // 68
        put("UADH", new StockInfo("UADH", "Universal Auto Distributors Holding", "/quote/bvmt/UADH/", OwnershipType.PRIVATE, "Motor Vehicles And Motor Vehicle Parts And Supplies", "Tunisia"));
        // 69
        put("CELL", new StockInfo("CELL", "Cellcom Société Anonyme", "/quote/bvmt/CELL/", OwnershipType.PRIVATE, "Electronic Parts and Equipment, not elsewhere classified", "Tunisia"));
        // 70
        put("STS", new StockInfo("STS", "Société Tunisienne du Sucre SA", "/quote/bvmt/STS/", OwnershipType.PRIVATE, "Canned, Frozen, And Preserved Fruits, Vegetables, and Food Specialties", "Tunisia"));
        // 71
        put("SIPHA", new StockInfo("SIPHA", "Société des Industries Pharmaceutiques de Tunisie - S.A.", "/quote/bvmt/SIPHA/", OwnershipType.PRIVATE, "Pharmaceutical Preparations", "Tunisia"));
        // 72
        put("SITEX", new StockInfo("SITEX", "Societe Industrielle des Textiles S.A.", "/quote/bvmt/SITEX/", OwnershipType.PRIVATE, "Textile Mill Products", "Tunisia"));
        // 73
        put("AETEC", new StockInfo("AETEC", "Advanced e-Technologies S.A", "/quote/bvmt/AETEC/", OwnershipType.PRIVATE, "Computer Programming, Data Processing, And Other Computer Related Services", "Tunisia"));
      }};

    // StockInfo as a Java Record
    record StockInfo(
            String symbol,
            String name,
            String url,
            OwnershipType ownershipType,
            String industry,
            String country
    ) {
        // Convenience constructors
        public StockInfo(String symbol, String name, String url) {
            this(symbol, name, url, OwnershipType.PRIVATE, "", "");
        }

        public StockInfo(String symbol, String name, String url, OwnershipType ownershipType) {
            this(symbol, name, url, ownershipType, "", "");
        }
    }
}