package eu.epnw.nxing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;

import eu.epnw.nxing.HttpException.BadRequestException;
import eu.epnw.nxing.HttpException.InternalServerErrorException;
import eu.epnw.nxing.HttpException.NotFoundException;
import eu.epnw.nxing.HttpException.UnauthorizedException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.NotSslRecordException;

@Sharable
public class ApiMapper extends SimpleChannelInboundHandler<FullHttpRequest> {

	private static final String HTTP_HEADER_CONTENT_LENGTH = "Content-Length";
	private static final String HTTP_HEADER_CONTENT_TYPE = "Content-Type";
	private static final String HTTP_HEADER_AUTHORIZATION = "Authorization";
	private static final String HTTP_HEADER_CONNECTION = "Connection";
	private static final String HTTP_HEADER_CONNECTION_VALUE = "close";

	private final boolean bDebug;
	private List<String> apiTokens;
	private final AtomicInteger totalDecodeRequests;
	private final AtomicInteger successfullDecodeRequests;

	public ApiMapper(boolean debug) {
		this.bDebug = debug;
		this.totalDecodeRequests = new AtomicInteger();
		this.successfullDecodeRequests = new AtomicInteger();
	}

	public int[] statistics() {
		return new int[] { totalDecodeRequests.get(), successfullDecodeRequests.get() };
	}

	public void setTokens(List<String> apiTokens) {
		this.apiTokens = apiTokens;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, final FullHttpRequest msg) throws Exception {
		try {
			checkAuthorization(msg);
			String uri = msg.uri();
			if (bDebug) {
				System.out.println("Requested " + msg.method() + " " + uri);
			}
			if (msg.method() == HttpMethod.POST) {
				if (uri.equals("/decode")) {
					handlePostDecode(ctx, msg);
				} else {
					throw new NotFoundException("Invalid resource " + msg.uri() + " for method " + msg.method());
				}
			} else {
				throw new NotFoundException("Invalid resource " + msg.uri() + " for method " + msg.method());
			}
		} catch (HttpException e) {
			if (bDebug) {
				e.printStackTrace();
			}
			FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
					HttpResponseStatus.valueOf(e.responseCode));
			response.headers().add(HTTP_HEADER_CONTENT_LENGTH, 0);
			response.headers().add(HTTP_HEADER_CONTENT_TYPE, "text/plain");
			response.headers().add(HTTP_HEADER_CONNECTION, HTTP_HEADER_CONNECTION_VALUE);
			ctx.writeAndFlush(response);
		}
	}

	private void checkAuthorization(FullHttpRequest msg) throws UnauthorizedException {
		if (apiTokens != null) {
			String auth = msg.headers().get(HTTP_HEADER_AUTHORIZATION);
			if (auth == null) {
				throw new UnauthorizedException("Bad token null");
			} else if (!apiTokens.contains(auth)) {
				throw new UnauthorizedException("Bad token " + auth);
			}
		}
	}

	private void handlePostDecode(ChannelHandlerContext ctx, final FullHttpRequest msg)
			throws BadRequestException, InternalServerErrorException {
		try {
			totalDecodeRequests.incrementAndGet();
			String code = ImageTools.decodeQRCode(msg.content());
			successfullDecodeRequests.incrementAndGet();
			ByteBuf content = Unpooled.wrappedBuffer(code.getBytes(StandardCharsets.UTF_8));
			FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
					content);
			response.headers().add(HTTP_HEADER_CONTENT_LENGTH, content.readableBytes());
			response.headers().add(HTTP_HEADER_CONTENT_TYPE, "text/plain");
			response.headers().add(HTTP_HEADER_CONNECTION, HTTP_HEADER_CONNECTION_VALUE);
			ctx.writeAndFlush(response);
		} catch (DataFormatException e1) {
			throw new BadRequestException(e1);
		} catch (com.google.zxing.NotFoundException e2) {
			throw new BadRequestException(e2);
		} catch (Exception e3) {
			throw new InternalServerErrorException(e3);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		if (cause instanceof IOException && cause.getMessage().equals("Connection reset by peer")) {

		} else if (cause instanceof NotSslRecordException) {
			System.out.println("Attempt for a no-ssl connection by " + ctx.channel().remoteAddress());
		} else {
			super.exceptionCaught(ctx, cause);
		}
	}
}
