"""Tiny standard-library clients for the chapter 29 and 30 experiments, so they run with nothing but
python3: the PostgreSQL simple-query protocol (trust or password-less local auth) and Redis RESP2.
Not production clients: no TLS, no SCRAM, no extended protocol."""
import os
import socket
import struct


class PgError(Exception):
    def __init__(self, fields):
        self.fields = fields
        super().__init__(f"{fields.get('C')}: {fields.get('M')}")


class Pg:
    """PGHOST may be a directory (Unix socket) or a host name; PGPORT, PGUSER, PGDATABASE as in libpq."""

    def __init__(self, dbname=None, host=None, port=None):
        host = host or os.environ.get("PGHOST", "localhost")
        port = int(port or os.environ.get("PGPORT", "5432"))
        user = os.environ.get("PGUSER", "postgres")
        dbname = dbname or os.environ.get("PGDATABASE", "c29")
        if host.startswith("/"):
            self.sock = socket.socket(socket.AF_UNIX)
            self.sock.connect(f"{host}/.s.PGSQL.{port}")
        else:
            self.sock = socket.create_connection((host, port))
            self.sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self.buf = b""
        body = struct.pack("!i", 196608) + b"".join(
            k.encode() + b"\0" + v.encode() + b"\0" for k, v in (("user", user), ("database", dbname))) + b"\0"
        self.sock.sendall(struct.pack("!i", len(body) + 4) + body)
        while True:
            t, payload = self._msg()
            if t == b"R" and struct.unpack("!i", payload[:4])[0] != 0:
                raise RuntimeError("only trust authentication is supported by this tiny client")
            if t == b"E":
                raise PgError(self._fields(payload))
            if t == b"Z":
                break

    def _read(self, n):
        while len(self.buf) < n:
            chunk = self.sock.recv(65536)
            if not chunk:
                raise ConnectionError("server closed the connection")
            self.buf += chunk
        out, self.buf = self.buf[:n], self.buf[n:]
        return out

    def _msg(self):
        head = self._read(5)
        return head[:1], self._read(struct.unpack("!i", head[1:])[0] - 4)

    @staticmethod
    def _fields(payload):
        out = {}
        for part in payload.split(b"\0"):
            if part:
                out[chr(part[0])] = part[1:].decode(errors="replace")
        return out

    def query(self, sql):
        """Runs one or more statements; returns the rows of the last result (strings or None).
        Raises PgError after the server is ready again, so the connection stays usable."""
        data = sql.encode() + b"\0"
        self.sock.sendall(b"Q" + struct.pack("!i", len(data) + 4) + data)
        rows, error = [], None
        while True:
            t, p = self._msg()
            if t == b"T":
                rows = []
            elif t == b"D":
                n = struct.unpack("!h", p[:2])[0]
                pos, row = 2, []
                for _ in range(n):
                    ln = struct.unpack("!i", p[pos:pos + 4])[0]
                    pos += 4
                    if ln < 0:
                        row.append(None)
                    else:
                        row.append(p[pos:pos + ln].decode())
                        pos += ln
                rows.append(tuple(row))
            elif t == b"E":
                error = PgError(self._fields(p))
            elif t == b"Z":
                if error:
                    raise error
                return rows

    def close(self):
        try:
            self.sock.sendall(b"X" + struct.pack("!i", 4))
        finally:
            self.sock.close()


class Redis:
    def __init__(self, host=None, port=None):
        host = host or os.environ.get("REDIS_HOST", "localhost")
        port = int(port or os.environ.get("REDIS_PORT", "6379"))
        self.sock = socket.create_connection((host, port))
        self.sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self.f = self.sock.makefile("rb")

    def __call__(self, *args):
        parts = [b"*%d\r\n" % len(args)]
        for a in args:
            b = a if isinstance(a, bytes) else str(a).encode()
            parts.append(b"$%d\r\n%s\r\n" % (len(b), b))
        self.sock.sendall(b"".join(parts))
        return self._reply()

    def _reply(self):
        line = self.f.readline()
        kind, rest = line[:1], line[1:-2]
        if kind == b"+":
            return rest.decode()
        if kind == b"-":
            raise RuntimeError(rest.decode())
        if kind == b":":
            return int(rest)
        if kind == b"$":
            n = int(rest)
            if n < 0:
                return None
            data = self.f.read(n + 2)[:-2]
            return data.decode()
        if kind == b"*":
            n = int(rest)
            return None if n < 0 else [self._reply() for _ in range(n)]
        raise RuntimeError(f"unexpected reply {line!r}")

    def close(self):
        self.sock.close()
