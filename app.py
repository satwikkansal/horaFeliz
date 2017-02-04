"""
Flask Documentation:     http://flask.pocoo.org/docs/
Jinja2 Documentation:    http://jinja.pocoo.org/2/documentation/
Werkzeug Documentation:  http://werkzeug.pocoo.org/documentation/

This file creates your application.
"""

import os
from flask import Flask, render_template, request, redirect, url_for
import json
import datetime
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
    data = request.form
    label_data = data.get("label_data").upper()
    ocr_data = data.get("ocr_data")
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
	result["label_match"] = is_similar
	result["prices_match"] = prices_match
	return json.dumps(result)



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

def find_similarity(s,li):
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
    #clean up the string
    num_str = re.sub('[^A-Za-z0-9$]+', '',num_str)
    if num_str:
    	decimal_price = re.findall(r'\$(\d+)', num_str)[0]
    	print '='*10, decimal_price
    	final_price = decimal_price
    	return final_price

if __name__ == '__main__':
    app.run(debug=True)
