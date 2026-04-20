#include "WebnovelScraper.h"
#include "TitanLog.h"
#include <regex>

/**
 * WebnovelScraper Implementation
 * 
 * THE HYBRID PATH:
 * This code defines HOW to find books, but Kotlin defines HOW to get the bytes.
 */

std::vector<ScraperResult> WebnovelScraper::search(const std::string& query) {
    TitanTimer timer("WebnovelScraper::search");
    std::vector<ScraperResult> results;

    // 1. Build the search URL (RoyalRoad style for demo)
    std::string searchUrl = "https://www.royalroad.com/fictions/search?title=" + query;
    
    TitanLog::d("Scraper", "Fetching search page: %s", searchUrl.c_str());
    
    // 2. Call back to Kotlin to fetch the HTML
    std::string html = m_fetcher(searchUrl);
    
    if (html.empty()) {
        TitanLog::w("Scraper", "Failed to fetch HTML or received empty response");
        return results;
    }

    // 3. Titan Regex Parsing (Phase 13 Atomic Logic)
    // We look for: <h2 class="fiction-title"><a href="URL">TITLE</a></h2>
    // and: <img class="img-responsive" src="COVER_URL">
    
    try {
        std::regex titleRegex("<h2 class=\"fiction-title\">\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>");
        std::regex coverRegex("<img[^>]*src=\"([^\"]+)\"[^>]*class=\"[^\"]*img-responsive[^\"]*\"");

        auto titleBegin = std::sregex_iterator(html.begin(), html.end(), titleRegex);
        auto titleEnd = std::sregex_iterator();

        for (std::sregex_iterator i = titleBegin; i != titleEnd; ++i) {
            std::smatch match = *i;
            ScraperResult res;
            res.url = "https://www.royalroad.com" + match[1].str();
            res.title = match[2].str();
            res.source = "RoyalRoad";
            
            // For the demo, we use a placeholder cover or try to find one nearby
            res.coverUrl = "https://www.royalroad.com/build/img/rr-logo-gold.png"; 

            results.push_back(res);
            
            // Limit to 10 results for the demo
            if (results.size() >= 10) break;
        }
    } catch (const std::exception& e) {
        TitanLog::e("Scraper", "Regex parsing failed: %s", e.what());
    }

    TitanLog::i("Scraper", "Found %zu results for query: %s", results.size(), query.c_str());
    return results;
}
