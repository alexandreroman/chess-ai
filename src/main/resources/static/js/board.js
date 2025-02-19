const DEBUG = false;
let _cid = "";

function subscribeToChessBoardUpdates(cid) {
    _cid = cid;
}

function copyBoardStateToClipboard() {
    const movesStr = $('#moves').val();
    navigator.clipboard.writeText(movesStr)
        .then(() => {
            bootstrap.Toast.getOrCreateInstance(document.getElementById('copyToast')).show();
        })
        .catch(err => {
            console.error("Failed to copy moves to clipboard: ", err);
        });
}

function _initPopups() {
    const popoverTriggerList = document.querySelectorAll('[data-bs-toggle="popover"]')
    const popoverList = [...popoverTriggerList].map(popoverTriggerEl => new bootstrap.Popover(popoverTriggerEl))
}

let stompClient;

function _initStompClient() {

    const stompConfig = {
        brokerURL: "/ws",

        debug: function (str) {
            if (DEBUG) console.log('STOMP: ' + str);
        },

        onConnect: function (str) {
            const cid = _cid;
            if (DEBUG) console.log("Subscribed to chess board updates: " + cid);

            const topic = '/topic/chess/' + cid;
            const sub = stompClient.subscribe(topic, function (message) {
                const payload = JSON.parse(message.body);
                if (DEBUG) console.log("Received event for chess " + cid + ": " + payload.type);

                if (payload.type === "UPDATE_BOARD") {
                    if (DEBUG) console.log("Updating chess " + cid);
                    htmx.ajax('GET', '/chess/' + cid + '/board', "#chess-board")
                }
            });
        },

        reconnectDelay: 1000
    };

    // Create an instance.
    stompClient = new StompJs.Client(stompConfig);

    // Attempt to connect.
    stompClient.activate();
}

$(document).ready(function () {
    _initStompClient();
    _initPopups();

    document.addEventListener('keydown', (event) => {
        if (event.key === "Escape" || event.keyCode === 27) {
            new bootstrap.Collapse($('#ai-dialog')).hide();
        }
    });
})
