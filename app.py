import os
from flask import Flask, render_template, request, redirect, url_for, jsonify
import json
import datetime
import difflib
import pandas as pd
import numpy as np
import re
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.feature_extraction.text import TfidfVectorizer

CSV_FILENAME = "recommended_prices.csv"
LOG_FILE = "log.txt"

DATE = '04/01/2015'

f = pd.read_csv(CSV_FILENAME)
df = pd.DataFrame(f)


app = Flask(__name__)

app.config['SECRET_KEY'] = os.environ.get('SECRET_KEY', 'this_should_be_configured')


###
# Routing for your application.
###

@app.route('/')
def home():
    """Render website's home page."""
    return render_template('home.html')


@app.route('/about/')
def about():
    """Render the website's about page."""
    return render_template('about.html')

@app.route('/verify', methods=['POST'])
def verify_scanned_data():
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
    if ocr_data and label_data:
	    num_cans = label_data.split(" ")[-1]
	    brand_name = ' '.join(label_data.split(' ')[:-1])
	    print '='*10
	    print brand_name
	    print '='*10
	    similarity_score = find_similarity(label_data,ocr_data)
	    if num_cans in ocr_data:
	    	similarity_score += 0.05
	    print '='*10
	    print similarity_score
	    print '='*10
	    is_similar = False
	    if similarity_score >= 0.1:
	    	is_similar = True
	    	price = str(extract_prices(ocr_data))
	    	print '='*10
	    	print price
	    	print '='*10
	    	db_price = str(int(list(df[(df["Date"] == DATE) & (df["Brand"] == brand_name.upper())]["Recommended Price"])[0]*100))
	    	if price == db_price:
	    		prices_match = True
	    	else:
	    		if price.split('.')[0] == db_price.split('.')[0]:
	    			prices_match == True
	    		else:
	    			with open(LOG_FILE, 'a') as f:
	    				log_str = "Mismatch in the price of {} detected at {}.\
	    				 \n Recommended Price \t:{} \n Selling Price \t: {} \n".format(label_data, str(datetime.datetime.now()), db_price, price)
	    				f.write(log_str)
	    else:
	    	with open(LOG_FILE, 'a') as f:
		    	log_str = "Labels not correctly placed for {} \n detected at time : {}.\
		    			  ".format(label_data, str(datetime.datetime.now()))
		    	f.write(log_str)
    result["label_match"] = is_similar
    result["prices_match"] = prices_match
    return jsonify(result)


###
# The functions below should be applicable to all Flask apps.
###

@app.route('/<file_name>.txt')
def send_text_file(file_name):
    """Send your static text file."""
    file_dot_text = file_name + '.txt'
    return app.send_static_file(file_dot_text)


@app.after_request
def add_header(response):
    """
    Add headers to both force latest IE rendering engine or Chrome Frame,
    and also to cache the rendered page for 10 minutes.
    """
    response.headers['X-UA-Compatible'] = 'IE=Edge,chrome=1'
    response.headers['Cache-Control'] = 'public, max-age=600'
    return response


@app.errorhandler(404)
def page_not_found(error):
    """Custom 404 page."""
    return render_template('404.html'), 404

def lexical_similarity(s,li):
    '''
    Returns lexical similarity score of s with all the strings
    in li.
    '''
    x = np.ones((len(li)+1,len(li)+1))
    li.append(s)
    vect = TfidfVectorizer(min_df=1)
    tfidf = vect.fit_transform(li)
    x = cosine_similarity(tfidf[-1],tfidf)
    print(x[-1])
    return x[-1]

def chracter_similarity(a, b):
    return difflib.SequenceMatcher(a=a.lower(), b=b.lower()).ratio()

def find_similarity(a, b):
    a = a.lower().strip('\n')
    b = b.lower().strip('\n')
    return 2*lexical_similarity(a, [b])[0] + chracter_similarity(a, b)


def closest_matching_answer(question):
    ques = list(df["Question"])
    print list(ques)
    similarities = find_similarity(question, ques)
    mx = -1
    idx = -1
    for ix,v in enumerate(similarities[:-1]):
        if v>mx:
            idx = ix
            mx = v
            print(ix)
    return df["Answer"][idx]

def extract_prices(num_str):
    #extracts prices from ocr_data
    if num_str:
    	decimal_price = '12.49'
    	if re.findall(r'\$(\d+)', num_str):
    		decimal_price = re.findall(r'\$(\d+)', num_str)[0]
    	print '='*10, decimal_price
    	final_price = decimal_price
    	return final_price

b_price = df[(df["Date"] == DATE) & (df["Brand"] == "Brooklyn Pilsner".upper())]
print b_price
if __name__ == '__main__':
    app.run(debug=True)
