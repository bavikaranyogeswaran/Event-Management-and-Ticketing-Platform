import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";

export default function App() {
  return (
    <Container maxWidth="md">
      <Box sx={{ py: 8, textAlign: "center" }}>
        <Typography variant="h3" component="h1" gutterBottom>
          Event Management and Ticketing Platform
        </Typography>
        <Typography color="text.secondary">
          Frontend scaffold ready — pages coming soon.
        </Typography>
      </Box>
    </Container>
  );
}
