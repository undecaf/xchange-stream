package info.bitrich.xchangestream.bitstamp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.bitstamp.dto.BitstampWebSocketTransaction;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.pusher.PusherStreamingService;
import io.reactivex.Observable;
import org.knowm.xchange.bitstamp.BitstampAdapters;
import org.knowm.xchange.bitstamp.dto.marketdata.BitstampOrderBook;
import org.knowm.xchange.bitstamp.dto.marketdata.BitstampTicker;
import org.knowm.xchange.bitstamp.dto.marketdata.BitstampTransaction;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

public class BitstampStreamingMarketDataService implements StreamingMarketDataService {
    private final PusherStreamingService service;

    BitstampStreamingMarketDataService(PusherStreamingService service) {
        this.service = service;
    }

    @Override
    public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
        String channelName = "order_book" + getChannelPostfix(currencyPair);

        return service.subscribeChannel(channelName, "data")
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    BitstampOrderBook bitstampOrderBook = mapper.readValue(s, BitstampOrderBook.class);
                    bitstampOrderBook = new BitstampOrderBook(new Date().getTime(), bitstampOrderBook.getBids(), bitstampOrderBook.getAsks());

                    return BitstampAdapters.adaptOrderBook(bitstampOrderBook, CurrencyPair.BTC_USD, 1000);
                });
    }

    @Override
    public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
        // BitStamp has now live ticker, only trades.
        throw new UnsupportedOperationException();
    }

    @Override
    public Observable<Trades> getTrades(CurrencyPair currencyPair, Object... args) {
        String channelName = "live_orders" + getChannelPostfix(currencyPair);

        return service.subscribeChannel(channelName, Arrays.asList("order_created", "order_changed", "order_deleted"))
                .map(s -> {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    BitstampWebSocketTransaction transactions = mapper.readValue(s, BitstampWebSocketTransaction.class);

                    Trade trade = BitstampAdapters.adaptTrade(transactions, CurrencyPair.BTC_USD, 1000);
                    return new Trades(Collections.singletonList(trade), Trades.TradeSortType.SortByTimestamp);
                });
    }

    private String getChannelPostfix(CurrencyPair currencyPair) {
        if (currencyPair.equals(CurrencyPair.BTC_USD)) {
            return "";
        }
        return "_" + currencyPair.base.toString().toLowerCase() + currencyPair.counter.toString().toLowerCase();
    }
}