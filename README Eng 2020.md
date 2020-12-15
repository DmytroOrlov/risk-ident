# Coding Challenge

## Problem Statement

As marketing representative I would like to transform and forward the current product list of our online shops to an external service provider.

The input is the current list of articles in CSV format. An article is a variant of a product and a product may have multiple articles (e.g. different colour shirts). In the input the articles for a particular product will always be next to each other.

For each product, only the cheapest article that is in stock should be included in the result. The details in the output format should come from this article.

The stock in the output should be the sum of the stocks of all articles of the product.

If no articles of a product are in stock, then the product should not appear in the output at all.

If multiple articles have the same price, the first one in the input should be used.

Your solution should do the following:

- Retrieve current list of articles from an HTTP endpoint
- Do a PUT of the results to an HTTP endpoint

The code should consist mostly of Scala and should be in a state that you would describe as "production ready".

## Test Service

A test service is provided for you to test your implementation against.

- Start: `java -jar coding-challenge.jar`

### Description of the Download endpoint
- HTTP GET `/articles/:lines`
 - `lines` specifies the number of returned articles
- Response Body: the CSV file

### Description of the Upload endpoint
- HTTP PUT `/products/:lines`, Content-Type: text/csv
 - `lines` refers to the number of underlying articles
- Response Code: 200 if all entries have been successfully processed

### Format of the source file
- separator: pipe (|)
- Line separator: LF (\n)
- Line 1: header / column names in German
- Columns: id|produktId|name|beschreibung|preis|bestand (String|String|String|String|Float|Int)
- Note: the delimiter will never be present in the values

### Format of the destination file
- separator: pipe (|)
- Line separator: LF (\n)
- Line 1: header / column names in German
- Columns: produktId|name|beschreibung|preis|summeBestand (String|String|String|Float|Int)

### German column name translation
- "beschreibung" (german) means "description"
- "preis" (german) means "price"
- "bestand" (german) means "stock"
- "summeBestand" (german) means "sum of stocks"

### Price formats
Prices have one or two decimal places and "." as separator between Euro and Cent without currency symbol.

For example: 12.13, 42.03 or 90.0