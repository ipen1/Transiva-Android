private final SessionManager sessionManager;

public TransivaBridge(Activity activity, WebView webView) {
    this.activity = activity;
    this.webView = webView;
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.sessionManager = new SessionManager(activity);
}
