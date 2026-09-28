import fs from 'node:fs';
import { SignJWT, jwtVerify } from "jose";
import bodyParser from "body-parser";
import jsonServer from "json-server";


process.loadEnvFile('.env');
/**
 * JSON Server is a lightweight and easy-to-use Node. js tool that simulates
 * a RESTful API using a JSON file as the data source.
 * You may replace the JSON server with your own API server and database.
 */
const server = jsonServer.create();

server.use(bodyParser.urlencoded({ extended: true }));
server.use(bodyParser.json());
server.use(jsonServer.defaults());

const secret = new TextEncoder().encode(process.env.JWT_SECRET)  // HMAC-SHA256 key

async function createToken({username}) {
  return new SignJWT({ user : username,})
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setExpirationTime("1h")
    .setIssuer("pkgpulse.com")
    .setAudience("pkgpulse-api")
    .sign(secret)
}

async function verifyToken(token) {
  const { payload } = await jwtVerify(token, secret, {
    issuer: "pkgpulse.com",
    audience: "pkgpulse-api",
  })
  return payload
}

// Check if the user exists
function isUser({ username, password }) {
  const userdb = JSON.parse(fs.readFileSync('./users.json', 'UTF-8'));
  return (
    userdb.users.findIndex(
      (user) => user.username === username && user.password === password
    ) !== -1
  );
}

// Check if the user is registered
function isRegistered({ username }) {
  const userdb = JSON.parse(fs.readFileSync('./users.json', 'UTF-8'));
  return (
    userdb.users.findIndex(
      (user) => user.username === username
    ) !== -1
  );
}

// API endpoint for user registration
server.post('/api/auth/register', (req, res) => {
    const { username, password } = req.body;
    if (isRegistered({ username }) === true) {
      const status = 401;
      const message = 'Credentials already exist';
      res.status(status).json({ status, message });
      return;
    }

    fs.readFile('./users.json', (err, data) => {
        if (err) {
          const status = 401;
          const message = err;
          res.status(status).json({ status, message });
          return;
        }
        let userData = JSON.parse(data.toString());
        const last_item_id = userData.users[userData.users.length - 1].id;
        userData.users.push({ id: last_item_id + 1, username:username, password:password }); //add some data
        fs.writeFile('./users.json',
        JSON.stringify(userData),
        (err, result) => {  // WRITE
            if (err) {
                const status = 401;
                const message = err;
                res.status(status).json({ status, message });
                return;
            }
        });
    });

    const access_token = createToken({ username,});
    res.status(200).json({ access_token });
});

// API endpoint for user login
server.post('/api/auth/login', (req, res) => {
  const { username, password } = req.body;
  if (isUser({ username, password }) === false) {
    const status = 401;
    const message = 'Incorrect username or password';
    res.status(status).json({ status, message });
    return;
  }
  console.log('LIVE');
  const access_token = createToken({ username,});
  res.status(200).json({ access_token });
});

server.listen(8081, () => {
  console.log('Running Auth API Server');
});