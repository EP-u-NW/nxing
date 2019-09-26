# Nxing
A server that just provides the qr- and barcode decoding of [zxing](https://github.com/zxing/zxing) over http(s) using [netty](https://netty.io/).

## Configuration
Configuration can be achived using the config.json.
The config.json has to be in the `workdir`. The `workdir` is either the current directory, or it can be passed as an argument.
E.g.: `java -jar nxing.jar /home/docker` to use /home/docker as `workdir`.
The config.json must contain all the follwing fields:

| Field        | Data Type | Description                       |
|--------------|---------------------------------------------------|------------------------------------|
| host         | string    | The address the server listens on.                                                                          |
| port         | integer   | The port the server listens on.                                                                             |
| maxImageSize | integer   | The maximum size an image may have to be accepted, in bytes.                                                |
| debug        | boolean   | Whether to start in `debug mode` or not. In `debug mode`, additional information is printed to the console. |
| useSsl       | boolean   | Whether to use ssl or not.                                                                                  |
| sslDir       | string    | The only field that may be null or omitted.<br>If so and `useSsl` is `true` a self signed certificate will be generated and use to secure connections.<br>Otherwise it has to point to a directory where the two files `fullchain.pem` and `privkey.pem` are located.<br>Note that this path is not relative to `workdir`. |

Example:
	{
		"port":7642,
		"host":"0.0.0.0",
		"debug":true,
		"sslDir":null,
		"useSsl":true,
		"maxImageSize":2097152,
	}

## Authorization
If the service should only be used by some known users, you can give them an api token and enable authorization.
To do this, simply store some api tokens in `tokens.txt`, one line per token (utf8), in the `workdir`.
If this file is present and not empty, a client needs to authorize itselfe by sending the http Authorization header with the value set to an valid token.

## List of Endpoints
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
	
## Commands
While the service is running, there are several commands that can be executed:
| Command | Description                                                                                        |
|---------|----------------------------------------------------------------------------------------------------|
| help    | Gives an overview to the available commands.                                                       |
| exit    | Shoots the server down gracefully.                                                                 |
| reload  | Reloads the content of `tokens.txt`.                                                               |
| stats   | Prints some statistics, such as how many decode requests where made and how many where successful. |