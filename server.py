import json, os, socket
from http.server import HTTPServer, SimpleHTTPRequestHandler

DB_FILE = os.path.join(os.path.dirname(__file__), 'registro_nc.json')
HTML_FILE = os.path.join(os.path.dirname(__file__), 'maag-qc-incoming.html')

class Handler(SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        pass  # silenzia i log HTTP

    def do_GET(self):
        if self.path == '/' or self.path == '/index.html':
            self.serve_file(HTML_FILE, 'text/html; charset=utf-8')
        elif self.path == '/api/nc':
            if os.path.exists(DB_FILE):
                with open(DB_FILE, 'r', encoding='utf-8') as f:
                    data = f.read()
            else:
                data = '[]'
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(data.encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == '/api/nc':
            length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(length)
            with open(DB_FILE, 'w', encoding='utf-8') as f:
                f.write(body.decode('utf-8'))
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(b'{"ok":true}')
        else:
            self.send_response(404)
            self.end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

    def serve_file(self, path, content_type):
        try:
            with open(path, 'rb') as f:
                data = f.read()
            self.send_response(200)
            self.send_header('Content-Type', content_type)
            self.end_headers()
            self.wfile.write(data)
        except FileNotFoundError:
            self.send_response(404)
            self.end_headers()

def get_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return '127.0.0.1'

if __name__ == '__main__':
    PORT = 5000
    ip = get_ip()
    server = HTTPServer(('0.0.0.0', PORT), Handler)

    print('')
    print('  ========================================')
    print('   MAAG QC Incoming - Server avviato!')
    print('  ========================================')
    print('')
    print('  Questo PC:  http://localhost:{}'.format(PORT))
    print('  Altri PC:   http://{}:{}'.format(ip, PORT))
    print('')
    print('  Tieni questa finestra aperta.')
    print('  Per fermare il server: premi CTRL+C')
    print('')

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print('\n  Server fermato.')
