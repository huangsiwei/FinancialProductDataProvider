package dataprovider

class CrawlerController {

    FinancialProductCrawlerService financialProductCrawlerService

    def index() {}

    def test() {
        financialProductCrawlerService.crawlerAllFinancialProduct()
    }

}
