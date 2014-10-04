package dataprovider

class CrawlerController {

    FinancialProductCrawlerService financialProductCrawlerService

    def index() {}

    def test() {
        financialProductCrawlerService.crawlerAllFinancialProduct()
    }

    def testFunction(){
        def bigList = ["A","B","C"]
        bigList.each { ins ->
            println(ins)
            bigList - ins
        }

    }
}
