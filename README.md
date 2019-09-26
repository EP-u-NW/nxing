# Nxing
A server that just provides the qr- and barcode decoding of zxing[https://github.com/zxing/zxing] over http(s) using netty[https://netty.io/].

# Configuration
Configuration can be achived using the config.json

# Authorization
If the service should only be used by some known users, you can give them an api token and enable authorization.
To do this, simply store some api tokens in tokens.txt, one line per token (utf8).
If this file is present and not empty, a client needs to authorize itselfe by sending the http Authorization header with the value set to a valid token.

# List of Endpoints
	POST /decode
		Requests:
			Decoding of code in the image
			Body-Format: Image bytes
		Returns:
			A 200 OK and the decoding result as body if everything worked
			A 400 Bad Request if the image did not contain something to decode or is not a png or jpg
			A 401 Unauthorized if the server requires authorization and no valid authorization was provided in the request
			A 413 Payload to Large if the content exceeds the maxImageSize
			A 500 Internal Server Error if something went wrong internally
	