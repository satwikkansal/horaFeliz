data = json.loads(request.form.to_dict().keys()[0])
    print(data)
    label_data = data.get("label_data")
    print(label_data)
    ocr_data = data.get("ocr_data")
    print(ocr_data)	
    prices_match = False
    result = {
    	"label_match":False,
    	"prices_match":False
    }
    
    similarity_score = find_similarity(label_data,[ocr_data])
    print '='*10, similarity_score
    is_similar = False
    if similarity_score[0] >= 0.4:
    	is_similar = True
    	price = str(extract_prices(ocr_data))
    	print "="*10, similarity_score
    	db_price = str(int(list(df[(df["Date"] == DATE) & (df["Brand"] == label_data)]["Recommended Price"])[0]*100))
    	if price == db_price:
    		prices_match = True
    	else:
    		if price.split('.')[0] == db_price.split('.')[0]:
    			prices_match == True
    		else:
    			with open(LOG_FILE, 'a') as f:
    				f.write("Mismatch in the price of %s detected at %s.\
    				 \n Recommended Price \t:%s \n Selling Price \t: %s"
    				  %(label_data, str(datetime.datetime.now()), db_price, price))
	 	print("YAY!")
		result["label_match"] = is_similar
		result["prices_match"] = prices_match
		return "success"
	return "No"