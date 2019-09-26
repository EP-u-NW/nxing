package eu.epnw.nxing;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

public class Main {

	private static final String HELP = "help -> prints this\r\nstats -> prints statistics\r\nreload-> reloads api tokens from disk\r\nexit -> shoots down the server";

	private static String workDir = "./";

	public static void main(String[] args)
			throws JSONException, SQLException, IOException, InterruptedException, CertificateException {
		System.out.println("Starting server");
		if (args != null && args.length == 1 && args[0] != null) {
			workDir = args[0];
			if (!workDir.endsWith("/")) {
				workDir = workDir + "/";
			}
		}
		System.out.println("Working dir is " + workDir);
		System.out.println("You can change that by adding it as argument");
		final Config config = Config.load(workDir);
		if (config.debug) {
			System.out.println("Using PARANOID leak detection");
			ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
		}
		final SslContext sslContext;
		if (config.useSsl) {
			if (config.sslDir != null) {
				System.out.println("Creating SslContext...");
				sslContext = SslContextBuilder
						.forServer(new File(config.sslDir + "/fullchain.pem"), new File(config.sslDir + "/privkey.pem"))
						.build();
			} else {
				System.err.println("WARNING: Using self signed ssl certificate!");
				SelfSignedCertificate snaikoil = new SelfSignedCertificate();
				sslContext = SslContextBuilder.forServer(snaikoil.certificate(), snaikoil.privateKey()).build();
			}
		} else {
			System.out.println("Not using SSL!");
			sslContext = null;
		}
		final EventExecutorGroup buisnessLogic = new DefaultEventExecutorGroup(10);
		final ApiMapper apiMapper = new ApiMapper(config.debug);
		setTokens(apiMapper);
		ServerBootstrap bootstrap = new ServerBootstrap().group(new NioEventLoopGroup(), new NioEventLoopGroup())
				.channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ChannelPipeline pipline = ch.pipeline();
						if (sslContext != null) {
							pipline.addLast("ssl", sslContext.newHandler(ch.alloc()));
						}
						pipline.addLast("decoder", new HttpRequestDecoder());
						pipline.addLast("encoder", new HttpResponseEncoder());
						pipline.addLast("aggregator", new HttpObjectAggregator(config.maxImageSize + 2048));
						pipline.addLast(buisnessLogic, "apiMapper", apiMapper);
					}
				});
		SocketAddress address = new InetSocketAddress(config.host, config.port);
		bootstrap.bind(address).syncUninterruptibly();
		System.out.print("Server running on " + address);
		if (config.debug) {
			System.out.println(" in debug mode");
		} else {
			System.out.println();
		}
		handleCommands(apiMapper);
		System.out.println("Shooting down...");
		bootstrap.config().group().shutdownGracefully().syncUninterruptibly();
		bootstrap.config().childGroup().shutdownGracefully().syncUninterruptibly();
		buisnessLogic.shutdownGracefully().syncUninterruptibly();
		System.out.println("Bye");
	}

	private static List<String> apiTokens(String path) throws IOException {
		File file = new File(path);
		if (file.exists()) {
			String[] tokens = readUtf8String(new FileInputStream(file)).trim().replace("\r\n", "\n").split("\n");
			if (tokens.length == 0 || tokens[0].length() == 0) {
				return null;
			} else {
				return Arrays.asList(tokens);
			}
		} else {
			return null;
		}
	}

	private static void setTokens(ApiMapper mapper) throws IOException {
		String path = workDir + "tokens.txt";
		List<String> tokens = apiTokens(path);
		if (tokens == null) {
			mapper.setTokens(null);
			System.out.println("Disabled client authentication since file " + path + " does not exist or is empty");
		} else {
			mapper.setTokens(tokens);
			System.out.println("Loaded " + tokens.size() + " api tokens from " + path);
		}
	}

	private static void handleCommands(ApiMapper apiMapper) throws IOException {
		Scanner in = new Scanner(System.in);
		System.out.println("Waiting for commands");
		while (in.hasNextLine()) {
			try {
				String line = in.nextLine();
				if (line.equals("help")) {
					System.out.println(HELP);
				} else if (line.equals("stats")) {
					int[] stats = apiMapper.statistics();
					System.out.println("Total decode requests:       " + stats[0]);
					System.out.println("Successfull decode requests: " + stats[1]);
				} else if (line.equals("reload")) {
					setTokens(apiMapper);
				} else if (line.equals("exit")) {
					break;
				} else {
					System.out.println("Unknown command; use help for a list of commands");
				}
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		}
		in.close();
	}

	private static JSONObject fromFile(String path) throws IOException {
		InputStream in = new FileInputStream(path);
		String source = readUtf8String(in);
		in.close();
		return new JSONObject(source);
	}

	private static String readUtf8String(InputStream in) throws IOException {
		byte[] buffer = new byte[4096];
		String tmp = "";
		int offset = 0;
		while (true) {
			int r = in.read(buffer, offset, buffer.length - offset);
			if (r == -1) {
				break;
			} else {
				offset = offset + r;
				if (offset == buffer.length) {
					tmp = tmp + new String(buffer, StandardCharsets.UTF_8);
					offset = 0;
				}
			}
		}
		return tmp + new String(buffer, 0, offset, StandardCharsets.UTF_8);
	}

	private static class Config {
		public final String host, sslDir;
		public final int port, maxImageSize;
		public final boolean debug, useSsl;

		private Config(String host, String sslDir, int port, boolean debug, boolean useSsl, int maxImageSize) {
			this.maxImageSize = maxImageSize;
			this.useSsl = useSsl;
			this.sslDir = sslDir;
			this.host = host;
			this.port = port;
			this.debug = debug;
		}

		public static Config load(String dir) throws IOException {
			JSONObject data = fromFile(dir + "config.json");
			String sslDir;
			try {
				sslDir = data.getString("sslDir");
			} catch (JSONException e) {
				sslDir = null;
			}
			return new Config(data.getString("host"), sslDir, data.getInt("port"), data.getBoolean("debug"),
					data.getBoolean("useSsl"), data.getInt("maxImageSize"));
		}
	}
}
