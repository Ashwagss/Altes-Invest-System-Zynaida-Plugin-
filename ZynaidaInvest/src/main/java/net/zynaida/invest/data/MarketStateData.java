package net.zynaida.invest.data;

public record MarketStateData(double sentiment, long lastDividendTime, String masterServerId, long lastUpdate) {
}
