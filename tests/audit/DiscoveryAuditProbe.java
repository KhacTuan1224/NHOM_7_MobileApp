import com.rescuefarm.domain.enums.*;
import com.rescuefarm.domain.model.*;
import com.rescuefarm.ui.home.*;
import java.util.*;

/** Offline diagnostic against the compiled app classes. No Android/Firebase runtime or network. */
public class DiscoveryAuditProbe {
    public static void main(String[] args) {
        Date now = new Date(2000);
        Product product = Product.restore("p", "seller", "fruit", "Xoai", "", 50, 30, "kg", "", "",
                Collections.emptyList(), 0, 0, ProductStatus.ACTIVE);
        List<ProductBatch> batches = Arrays.asList(
                ProductBatch.restore("a", "p", null, new Date(1), new Date(5000), 6, 6, 0, 0, BatchStatus.AVAILABLE, 1),
                ProductBatch.restore("b", "p", null, new Date(1), new Date(6000), 6, 6, 0, 0, BatchStatus.AVAILABLE, 1));
        DiscoveryEngine engine = new DiscoveryEngine();
        Map<String,List<ProductBatch>> stock = Collections.singletonMap("p", batches);
        DiscoveryQuery minimum = new DiscoveryQuery("", "", "", null, null, null, DiscoveryQuery.Sort.RELEVANCE,
                0, Double.MAX_VALUE, 0, 0, 10);
        int plain = engine.search(Collections.singletonList(product), Collections.emptyList(), stock,
                DiscoveryQuery.empty(), null, null, now).size();
        int filtered = engine.search(Collections.singletonList(product), Collections.emptyList(), stock,
                minimum, null, null, now).size();
        int expired = engine.search(Collections.singletonList(product), Collections.emptyList(), stock,
                DiscoveryQuery.empty(), null, null, new Date(6000)).size();
        if (plain != 1 || expired != 0) throw new AssertionError("Unexpected normal/expiry regression");
        if (filtered != 1) throw new AssertionError("Minimum stock must sum the product's eligible live batches");
        System.out.println("BASELINE_OK: normal product visible; at all-batch expiry product hidden.");
        System.out.println("MIN_STOCK_PROBE: total=12; each_batch=6; min_stock=10; result_count=" + filtered);
        System.out.println("REGRESSION_OK: minimum stock uses the product's total eligible live stock.");
    }
}
